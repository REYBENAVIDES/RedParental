package com.example.redpaternal.datos.remoto

import android.util.Log
import com.example.redpaternal.datos.modelo.*
import com.example.redpaternal.utilidades.EncriptacionTplink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern
import java.util.concurrent.TimeUnit

class AyudanteTPLink(
    private val ipRouter: String,
    private val passwordUsuario: String
) {

    companion object {
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

    suspend fun autorizar() = withContext(Dispatchers.IO) {
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

    suspend fun cerrarSesion() = withContext(Dispatchers.IO) {
        try {
            realizarPeticion(codigo = 11, asincrono = 0, usarToken = true)
        } catch (e: Exception) {}
    }

    suspend fun obtenerEstado(): EstadoRouter = withContext(Dispatchers.IO) {
        try {
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

            fun ext(id: String, pre: String): String {
                return extractValue(bloques[id] ?: emptyList(), pre)
            }

            val macWan = ext("1|1,0,0", "mac 1 ")
            val macLan = ext("1|1,0,0", "mac 0 ")
            val ipWan = ext("23|1,0,0", "ip ")
            val ipLan = ext("4|1,0,0", "ip ")
            val gw = ext("23|1,0,0", "gateway ")
            val uptime = ext("23|1,0,0", "upTime ").toLongOrNull() ?: 0
            val dispositivos = parsearDispositivos(bloques["13|1,0,0"] ?: emptyList())
            return@withContext EstadoRouter(
                macWan = macWan, macLan = macLan, ipWan = ipWan, ipLan = ipLan,
                ipPuertaEnlace = gw, tiempoActivo = uptime / 100,
                dispositivos = dispositivos,
                wifi2gHabilitado = ext(REQ_WIFI_HOST_2G, "bEnable ") == "1",
                wifi5gHabilitado = ext(REQ_WIFI_HOST_5G, "bEnable ") == "1",
                invitados2gHabilitado = ext(REQ_WIFI_INVITADO_2G, "bEnable ") == "1",
                invitados5gHabilitado = ext(REQ_WIFI_INVITADO_5G, "bEnable ") == "1"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al obtener estado: ${e.message}")
            throw e
        }

    }

    private fun encriptarPasswordXor(pwd: String): String {
        return _encriptarXorBase(pwd, CLAVE_XOR_DEFECTO, DICCIONARIO_XOR_DEFECTO)
    }

    private fun codificarToken(passXor: String, auth1: String, auth2: String): String {
        return _encriptarXorBase(passXor, auth1, auth2)
    }

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
        val firma = encriptador.obtenerFirma(
            secuencia,
            data.length,
            nnRsa,
            eeRsa
        )

        return "sign=$firma\r\ndata=$data"
    }

    private fun desencriptarDatos(textoEnc: String): String {
        return encriptador.aesDesencriptar(textoEnc)
    }

    private fun extractValue(lineas: List<String>, prefix: String): String {
        return lineas.find { it.startsWith(prefix) }
            ?.substringAfter(prefix) ?: ""
    }

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
            val ip = datos["ip"] ?: "0.0.0.0"
            val macRaw = datos["mac"] ?: ""
            if (ip == "0.0.0.0" || macRaw.length < 10) {
                continue
            }

            val macFinal = macRaw.uppercase()
            val estaOnline = datos["online"] == "1"
            val tipoIntRaw = datos["type"]?.toIntOrNull() ?: -1
            val tipoIntProcesado = if (estaOnline) tipoIntRaw else -1
            val nombreLimpio = if (datos["name"].isNullOrEmpty()) "Sin Nombre" else datos["name"]!!

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

    suspend fun obtenerEstadoCompleto(): EstadoRouter = withContext(Dispatchers.IO) {
        var intentos = 0
        val maxIntentos = 3
        while (intentos < maxIntentos) {
            try {
                if (intentos == 0) Log.d(TAG, "Iniciando ciclo atómico...")
                else Log.w(TAG, "Reintentando ciclo atómico (Intento ${intentos + 1})...")
                autorizar()
                val estado = obtenerEstado()
                cerrarSesion()
                return@withContext estado

            } catch (e: Exception) {
                val mensaje = e.message ?: ""
                try { cerrarSesion() } catch (ex: Exception) { }
                if (mensaje.contains("408") || mensaje.contains("timeout") || mensaje.contains("reset")) {
                    intentos++
                    if (intentos >= maxIntentos) {
                        Log.e(TAG, "Fallo definitivo tras $maxIntentos intentos: $mensaje")
                        throw e
                    }
                } else {
                    throw e
                }
            }
        }
        throw Exception("Error desconocido en ciclo atómico")
    }

    private fun EncriptacionTplink.obtenerCadenaAes(): String {
        return "k=${this.obtenerLlaveStr()}&i=${this.obtenerIvStr()}"
    }

    suspend fun reiniciar() = withContext(Dispatchers.IO) {
        realizarPeticion(codigo = 6, asincrono = 1, usarToken = true)
    }

    suspend fun configurarWifi(wifi: TipoConexion, enable: Boolean) = withContext(Dispatchers.IO) {
        val id = when(wifi) {
            TipoConexion.ANFITRION_2G -> REQ_WIFI_HOST_2G
            TipoConexion.ANFITRION_5G -> REQ_WIFI_HOST_5G
            else -> return@withContext
        }
        val enInt = if (enable) 1 else 0
        val txt = "id $id\r\nbEnable $enInt"

        val cuerpo = encriptarCuerpo(txt)
        realizarPeticion(codigo = 1, asincrono = 0, usarToken = true, datos = cuerpo)
    }

    suspend fun obtenerFirmware(): Firmware = withContext(Dispatchers.IO) {
        val cuerpo = encriptarCuerpo("0|1,0,0")
        val resp = realizarPeticion(codigo = 2, asincrono = 1, usarToken = true, datos = cuerpo)
        val respClaro = desencriptarDatos(resp)

        val mapa = respClaro.trim().lines().associate {
            val p = it.split(" ", limit = 2)
            if (p.size == 2) p[0] to p[1] else "" to ""
        }

        return@withContext Firmware(
            versionHardware = URLDecoder.decode(mapa["hardVer"] ?: "", "UTF-8"),
            modelo = URLDecoder.decode(mapa["modelName"] ?: "", "UTF-8"),
            versionFirmware = URLDecoder.decode(mapa["softVer"] ?: "", "UTF-8")
        )
    }

    suspend fun soporta(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = realizarPeticion(codigo = 2, asincrono = 1, datos = "0|1,0,0")
            return@withContext resp.startsWith("00000")
        } catch (e: Exception) {
            return@withContext false
        }
    }
}