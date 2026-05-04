package com.example.redpaternal.datos.remoto

import android.util.Log
import com.example.redpaternal.datos.modelo.*
import com.example.redpaternal.utilidades.EncriptacionTplink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class AyudanteTPLink private constructor(
    private val ipRouter: String,
    private val passwordUsuario: String
) {

    companion object {
        private val mutex = Mutex()
        val pausarEscaneoFondo = AtomicBoolean(false)

        @Volatile
        private var instanciaActiva: AyudanteTPLink? = null

        fun obtenerInstancia(ip: String, pass: String): AyudanteTPLink {
            val actual = instanciaActiva
            if (actual != null && actual.ipRouter == ip && actual.passwordUsuario == pass) {
                return actual
            }
            return AyudanteTPLink(ip, pass).also { instanciaActiva = it }
        }

        private const val INDICE_TOKEN_AUTH_1 = 3
        private const val INDICE_TOKEN_AUTH_2 = 4
        private const val REQ_WIFI_HOST_2G = "33|1,1,0"
        private const val REQ_WIFI_HOST_5G = "33|2,1,0"
        private const val REQ_WIFI_INVITADO_2G = "33|1,2,0"
        private const val REQ_WIFI_INVITADO_5G = "33|2,2,0"
        private const val REQ_WIFI_IOT_2G = "33|1,9,0"
        private const val REQ_WIFI_IOT_5G = "33|2,9,0"
        private const val CLAVE_XOR_DEFECTO = "RDpbLfCPsJZ7fiv"
        private const val DICCIONARIO_XOR_DEFECTO = "yLwVl0zKqws7LgKPRQ84Mdt708T1qQ3Ha7xv3H7NyU84p21BriUWBU43odz3iP4rBL3cD02KZciXTysVXiV8ngg6vL48rPJyAUw0HurW20xqxv9aYb4M9wK1Ae0wlro510qXeU07kV57fQMc8L6aLgMLwygtc0F10a0Dg70TOoouyFhdysuRMO51yY5ZlOZZLEal1h0t9YQW0Ko7oBwmCAHoic4HYbUyVeU3sfQ1xtXcPcf1aT303wAQhv66qzW"
        private const val CARACTER_RELLENO = 187.toChar()

        private val almacenCookies = java.util.concurrent.ConcurrentHashMap<String, List<Cookie>>()
        private val gestorCookies = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                almacenCookies[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return almacenCookies[url.host] ?: ArrayList()
            }
        }
        private val clienteHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(gestorCookies)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private val urlBase = "http://$ipRouter"
    private val TAG = "AyudanteTPLink"
    private var nnRsa = ""
    private var eeRsa = ""
    private var secuencia: Long = 0
    private var token = ""
    private val encriptador = EncriptacionTplink()
    private val REGEX_DATOS = Pattern.compile("id (\\d+\\|\\d,\\d,\\d)\\r\\n(.*?)(?=\\r\\nid \\d+\\||$)", Pattern.DOTALL)

    /**
     * El Controlador Maestro: Hilo seguro, Semáforo y Ciclo Atómico.
     */
    private suspend fun <T> transaccionSegura(esManual: Boolean = false, bloque: suspend () -> T): T? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!esManual && pausarEscaneoFondo.get()) {
                Log.d(TAG, "⏹️ Escaneo pausado por comando manual en curso.")
                return@withLock null
            }

            var intentos = 0
            val maxIntentos = 3

            while (intentos < maxIntentos) {
                try {
                    if (intentos > 0) {
                        Log.w(TAG, "🔄 Reintentando ciclo atómico (Intento ${intentos + 1})... respirando.")
                        delay(2000)
                    }

                    // CICLO ATÓMICO OBLIGATORIO PARA RENOVAR SECUENCIA AES
                    token = ""
                    almacenCookies.remove(ipRouter)

                    autorizar()
                    val resultado = bloque()
                    return@withLock resultado

                } catch (e: Exception) {
                    val msg = e.message ?: ""

                    if (msg.contains("abort", ignoreCase = true) || msg.contains("reset", ignoreCase = true)) {
                        Log.w(TAG, "⚠️ Conexión abortada por el router. Posible éxito del comando de bloqueo.")
                        throw e
                    }

                    if (msg.contains("408") || msg.contains("timeout") || msg.contains("403") || msg.contains("401")) {
                        intentos++
                        if (intentos >= maxIntentos) {
                            Log.e(TAG, "❌ Router inalcanzable tras $maxIntentos intentos.")
                            throw e
                        }
                    } else {
                        throw e
                    }
                } finally {
                    // SIEMPRE liberamos el servidor web del router
                    try { cerrarSesion() } catch (e: Exception) {}
                }
            }
            throw Exception("Fallo en transacción segura")
        }
    }

    private suspend fun autorizar() {
        Log.d(TAG, "🔑 Iniciando sesión (Nueva Secuencia)...")
        val passXor = encriptarPasswordXor(passwordUsuario)
        val respParams = realizarPeticion(codigo = 2, asincrono = 1)
        val lineas = respParams.trim().lines()

        if (lineas.size < 5) throw Exception("Error params")

        val auth1 = lineas[INDICE_TOKEN_AUTH_1].trim()
        val auth2 = lineas[INDICE_TOKEN_AUTH_2].trim()
        val tokenRaw = codificarToken(passXor, auth1, auth2)
        token = URLEncoder.encode(tokenRaw, "UTF-8")

        val respRsa = realizarPeticion(codigo = 16, asincrono = 0, datos = "get", usarToken = true)
        val lineasRsa = respRsa.trim().lines()

        if (lineasRsa.size < 4) throw Exception("Error RSA")

        eeRsa = lineasRsa[1].trim()
        nnRsa = lineasRsa[2].trim()
        secuencia = lineasRsa[3].trim().toLongOrNull() ?: 0

        val cadenaAes = encriptador.obtenerCadenaAes()
        val aesEnc = EncriptacionTplink.rsaEncriptar(cadenaAes, nnRsa, eeRsa)

        realizarPeticion(codigo = 16, asincrono = 0, datos = "set $aesEnc", usarToken = true)
        realizarPeticion(codigo = 7, asincrono = 0, usarToken = true)
    }

    private suspend fun cerrarSesion() {
        if (token.isNotEmpty()) {
            realizarPeticion(codigo = 11, asincrono = 0, usarToken = true)
            token = ""
        }
    }

    // =========================================================================================
    // MÉTODOS PÚBLICOS
    // =========================================================================================

    suspend fun obtenerEstadoCompleto(): EstadoRouter? = transaccionSegura(esManual = false) {
        val ids = listOf(
            "1|1,0,0", "4|1,0,0", "23|1,0,0", "13|1,0,0",
            REQ_WIFI_HOST_2G, REQ_WIFI_HOST_5G,
            REQ_WIFI_INVITADO_2G, REQ_WIFI_INVITADO_5G,
            REQ_WIFI_IOT_2G, REQ_WIFI_IOT_5G
        )
        val txtReq = ids.joinToString("#")
        val cuerpo = encriptarCuerpo(txtReq)

        val resp = realizarPeticion(codigo = 2, asincrono = 1, usarToken = true, datos = cuerpo)
        val respClaro = desencriptarDatos(resp)
        val bloques = parsearRespuestaAMapa(respClaro)

        fun ext(id: String, pre: String): String = extractValue(bloques[id] ?: emptyList(), pre)

        val macWan = ext("1|1,0,0", "mac 1 ")
        val macLan = ext("1|1,0,0", "mac 0 ")
        val ipWan = ext("23|1,0,0", "ip ")
        val ipLan = ext("4|1,0,0", "ip ")
        val gw = ext("23|1,0,0", "gateway ")
        val uptime = ext("23|1,0,0", "upTime ").toLongOrNull() ?: 0
        val dispositivos = parsearDispositivos(bloques["13|1,0,0"] ?: emptyList())

        EstadoRouter(
            macWan = macWan, macLan = macLan, ipWan = ipWan, ipLan = ipLan,
            ipPuertaEnlace = gw, tiempoActivo = uptime / 100,
            dispositivos = dispositivos,
            wifi2gHabilitado = ext(REQ_WIFI_HOST_2G, "bEnable ") == "1",
            wifi5gHabilitado = ext(REQ_WIFI_HOST_5G, "bEnable ") == "1",
            invitados2gHabilitado = ext(REQ_WIFI_INVITADO_2G, "bEnable ") == "1",
            invitados5gHabilitado = ext(REQ_WIFI_INVITADO_5G, "bEnable ") == "1"
        )
    }

    suspend fun bloquearDispositivoLocal(nombre: String, mac: String) {
        pausarEscaneoFondo.set(true)
        Log.d(TAG, "⏸️ PAUSA GLOBAL para Bloqueo.")

        try {
            transaccionSegura(esManual = true) {
                val macConGuiones = mac.replace(":", "-").uppercase()
                val nombreSeguro = nombre.replace(" ", "_")
                val comando = "advanced bm -add list:black name:$nombreSeguro mac:$macConGuiones"

                Log.d(TAG, "🛠️ Ejecutando BLOQUEO: $comando")
                val cuerpo = encriptarCuerpo(comando)
                val respuestaEnc = realizarPeticion(codigo = 0, asincrono = 0, usarToken = true, datos = cuerpo)
                val respuestaClara = desencriptarDatos(respuestaEnc)

                if (!respuestaClara.contains("00000")) throw Exception("Rechazo del router: $respuestaClara")
                Log.d(TAG, "✅ Bloqueo aplicado con éxito.")
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("abort", ignoreCase = true) || msg.contains("reset", ignoreCase = true)) {
                Log.w(TAG, "⚠️ Auto-bloqueo detectado. Éxito asumido.")
            } else throw e
        } finally {
            pausarEscaneoFondo.set(false)
        }
    }

    suspend fun desbloquearDispositivoLocal(mac: String) {
        pausarEscaneoFondo.set(true)
        try {
            transaccionSegura(esManual = true) {
                val macConGuiones = mac.replace(":", "-").uppercase()
                val comando = "advanced bm -del list:black mac:$macConGuiones"

                val cuerpo = encriptarCuerpo(comando)
                val respuestaEnc = realizarPeticion(codigo = 0, asincrono = 0, usarToken = true, datos = cuerpo)
                val respuestaClara = desencriptarDatos(respuestaEnc)

                if (!respuestaClara.contains("00000")) throw Exception("Rechazo del router: $respuestaClara")
                Log.d(TAG, "✅ Desbloqueo aplicado con éxito.")
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("abort", ignoreCase = true) || msg.contains("reset", ignoreCase = true)) {
                Log.w(TAG, "⚠️ Auto-desbloqueo detectado. Éxito asumido.")
            } else throw e
        } finally {
            pausarEscaneoFondo.set(false)
        }
    }

    suspend fun obtenerFirmware(): Firmware = transaccionSegura(esManual = true) {
        val cuerpo = encriptarCuerpo("0|1,0,0")
        val resp = realizarPeticion(codigo = 2, asincrono = 1, usarToken = true, datos = cuerpo)
        val respClaro = desencriptarDatos(resp)

        val mapa = respClaro.trim().lines().associate {
            val p = it.split(" ", limit = 2)
            if (p.size == 2) p[0] to p[1] else "" to ""
        }

        Firmware(
            versionHardware = URLDecoder.decode(mapa["hardVer"] ?: "", "UTF-8"),
            modelo = URLDecoder.decode(mapa["modelName"] ?: "", "UTF-8"),
            versionFirmware = URLDecoder.decode(mapa["softVer"] ?: "", "UTF-8")
        )
    } ?: throw Exception("Transacción cancelada")

    // =========================================================================================
    // MÉTODOS PRIVADOS (Criptografía y Red - ¡INTACTOS!)
    // =========================================================================================

    private fun EncriptacionTplink.obtenerCadenaAes(): String = "k=${this.obtenerLlaveStr()}&i=${this.obtenerIvStr()}"
    private fun encriptarPasswordXor(pwd: String): String = _encriptarXorBase(pwd, CLAVE_XOR_DEFECTO, DICCIONARIO_XOR_DEFECTO)
    private fun codificarToken(passXor: String, auth1: String, auth2: String): String = _encriptarXorBase(passXor, auth1, auth2)

    private fun _encriptarXorBase(texto: String, llave: String, diccionario: String): String {
        val len = maxOf(texto.length, llave.length)
        val txtPad = texto.padEnd(len, CARACTER_RELLENO)
        val keyPad = llave.padEnd(len, CARACTER_RELLENO)
        val sb = StringBuilder()
        for (i in 0 until len) {
            val tc = txtPad[i].code
            val kc = keyPad[i].code
            val idx = (tc xor kc) % diccionario.length
            sb.append(diccionario[idx])
        }
        return sb.toString()
    }

    private fun encriptarCuerpo(texto: String): String {
        val data = encriptador.aesEncriptar(texto)
        val firma = encriptador.obtenerFirma(secuencia, data.length, nnRsa, eeRsa)
        return "sign=$firma\r\ndata=$data"
    }

    private fun desencriptarDatos(textoEnc: String): String = encriptador.aesDesencriptar(textoEnc)
    private fun extractValue(lineas: List<String>, prefix: String): String = lineas.find { it.startsWith(prefix) }?.substringAfter(prefix) ?: ""

    private fun realizarPeticion(codigo: Int, asincrono: Int, usarToken: Boolean = false, datos: String? = null): String {
        var url = "$urlBase/?code=$codigo&asyn=$asincrono"
        if (usarToken) url += "&id=$token"
        val builder = Request.Builder().url(url).header("Referer", "$urlBase/").header("Connection", "Close")
        if (datos != null) builder.post(datos.toRequestBody("text/plain".toMediaType()))
        else if (codigo == 2 && asincrono == 1 && !usarToken) builder.post("".toRequestBody(null))
        else builder.get()

        val resp = clienteHttp.newCall(builder.build()).execute()
        val cuerpo = resp.body?.string() ?: ""
        resp.close()

        if (!resp.isSuccessful) {
            if (!(codigo == 2 && asincrono == 1 && !usarToken && datos == null)) throw Exception("Error HTTP ${resp.code}: ${resp.message}")
        }
        return cuerpo
    }

    private fun parsearDispositivos(lineas: List<String>): List<DispositivoTplink> {
        val mapaDeDispositivos = parsearListaAMapas(lineas)
        val listaFinal = ArrayList<DispositivoTplink>()

        for (datos in mapaDeDispositivos.values) {
            val ip = datos["ip"] ?: datos["ipaddr"] ?: datos["ipAddr"] ?: "0.0.0.0"
            val macRaw = datos["mac"] ?: datos["macaddr"] ?: datos["macAddr"] ?: ""
            if (ip == "0.0.0.0" || macRaw.length < 10) continue

            val macFinal = macRaw.uppercase()
            val estaOnline = (datos["online"] == "1" || datos["is_online"] == "1")
            val tipoIntRaw = datos["type"]?.toIntOrNull() ?: -1
            val tipoIntProcesado = if (estaOnline) tipoIntRaw else -1

            val nombreExtraido = datos["name"] ?: datos["hostName"] ?: datos["hostname"]
            val nombreLimpio = if (nombreExtraido.isNullOrEmpty()) "Sin Nombre" else nombreExtraido

            listaFinal.add(DispositivoTplink(
                tipo = TipoConexion.desdeEntero(tipoIntProcesado),
                direccionMac = macFinal,
                direccionIp = ip,
                nombreHost = nombreLimpio,
                activo = estaOnline,
                velocidadBajada = datos["down"]?.toLongOrNull() ?: 0,
                velocidadSubida = datos["up"]?.toLongOrNull() ?: 0
            ))
        }
        return listaFinal
    }

    private fun parsearListaAMapas(lineas: List<String>): Map<Int, Map<String, String>> {
        val resultado = HashMap<Int, HashMap<String, String>>()
        for (linea in lineas) {
            val partes = linea.trim().split(Regex("\\s+"), 3)
            if (partes.size >= 2) {
                val clave = partes[0]
                val idStr = partes[1].replace(",", "")
                val id = idStr.toIntOrNull()
                if (id != null) {
                    val valor = if (partes.size == 3) partes[2].trim() else ""
                    val mapaAtributos = resultado.getOrPut(id) { HashMap() }
                    mapaAtributos[clave] = valor
                }
            }
        }
        return resultado
    }

    private fun parsearRespuestaAMapa(texto: String): Map<String, List<String>> {
        val m = HashMap<String, List<String>>()
        val matcher = REGEX_DATOS.matcher(texto)
        while (matcher.find()) {
            val k = matcher.group(1) ?: ""
            val v = matcher.group(2) ?: ""
            m[k] = v.trim().split("\r\n")
        }
        return m
    }
}