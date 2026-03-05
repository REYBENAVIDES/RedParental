package com.example.redpaternal.datos.remoto

import android.util.Log
import com.example.redpaternal.datos.modelo.SitioVisitado
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AyudanteNextDNS {

    private val API_KEY = "8edaef780d1cc662fbde3bb773ea3063d535f959" // Tu clave real
    private val BASE_URL = "https://api.nextdns.io"
    private val TAG = "NextDNS_DEBUG"

    private val SERVICIOS_PARENTAL = mapOf(
        "facebook" to "facebook",
        "instagram" to "instagram",
        "whatsapp" to "whatsapp",
        "tiktok" to "tiktok",
        "youtube" to "youtube",
        "netflix" to "netflix",
        "twitter" to "twitter"
    )


    // 2. MAPA PARA JUEGOS/SITIOS COMPLEJOS (Usan endpoint /denylist)
    // Lilith Games usa múltiples dominios raíz. Al bloquear estos, caen todos los subdominios (comm-hgame, psp-api, etc.)
    private val DOMINIOS_ESPECIALES = mapOf(
        "lilith games" to listOf("lilithgame.com", "lilithgames.com", "lilithcdn.com", "lilith.com","aliyuncs.com"),
        "free fire" to listOf("garena.com", "freefiremobile.com"),
        "roblox" to listOf("roblox.com", "rbxcdn.com")
    )
    private fun limpiarUrl(url: String): String {
        val partes = url.split(".")
        return if (partes.size > 2) "${partes[partes.size - 2]}.${partes.last()}" else url
    }


    suspend fun alternarBloqueoSitio(perfilId: String, nombreSitio: String, urlSitio: String, bloquear: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val nombreNorm = nombreSitio.lowercase()

                // A. ESTRATEGIA PARENTAL CONTROL (Apps Nativas)
                if (SERVICIOS_PARENTAL.containsKey(nombreNorm)) {
                    val servicioId = SERVICIOS_PARENTAL[nombreNorm]!!
                    return@withContext gestionarServicioParental(perfilId, servicioId, bloquear)
                }

                // B. ESTRATEGIA DENYLIST MULTIPLE (Lilith Games)
                if (DOMINIOS_ESPECIALES.containsKey(nombreNorm)) {
                    val listaDominios = DOMINIOS_ESPECIALES[nombreNorm]!!
                    var todoExito = true
                    for (dominio in listaDominios) {
                        // Si falla uno, seguimos intentando con los otros
                        val exito = gestionarDenylist(perfilId, dominio, bloquear)
                        if (!exito) todoExito = false
                    }
                    return@withContext todoExito
                }

                // C. ESTRATEGIA DENYLIST SIMPLE
                val dominioSimple = limpiarUrl(urlSitio)
                return@withContext gestionarDenylist(perfilId, dominioSimple, bloquear)

            } catch (e: Exception) {
                Log.e("AyudanteNextDNS", "❌ Error alternando bloqueo: ${e.message}")
                return@withContext false
            }
        }
    }

    // --- CORRECCIÓN AQUÍ ---
    private fun gestionarServicioParental(perfilId: String, servicioId: String, bloquear: Boolean): Boolean {
        // URL Base para servicios
        val baseUrl = "https://api.nextdns.io/profiles/$perfilId/parentalControl/services"

        val url: URL
        val metodo: String

        if (bloquear) {
            // BLOQUEAR: POST a la lista general
            url = URL(baseUrl)
            metodo = "POST"
        } else {
            // DESBLOQUEAR: DELETE al servicio específico (Más seguro que update)
            url = URL("$baseUrl/$servicioId")
            metodo = "DELETE"
        }

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = metodo
        conn.setRequestProperty("X-Api-Key", API_KEY)

        if (bloquear) {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            // Enviamos Objeto único, no Array. La API suele ser más tolerante así.
            val jsonBody = "{\"id\": \"$servicioId\", \"active\": true}"
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }
        }

        val responseCode = conn.responseCode
        // Aceptamos 200 (OK), 201 (Created), 204 (No Content) y 409 (Conflict - Ya estaba bloqueado, que cuenta como éxito)
        return responseCode in 200..299 || responseCode == 409
    }

    private fun gestionarDenylist(perfilId: String, dominio: String, bloquear: Boolean): Boolean {
        // Doc: POST /profiles/:id/denylist (Para agregar/bloquear)
        // Doc: DELETE /profiles/:id/denylist/:domain (Para quitar/desbloquear)

        val urlString = if (bloquear)
            "https://api.nextdns.io/profiles/$perfilId/denylist"
        else
            "https://api.nextdns.io/profiles/$perfilId/denylist/$dominio"

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = if (bloquear) "POST" else "DELETE"
        conn.setRequestProperty("X-Api-Key", API_KEY)

        if (bloquear) {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val jsonBody = "{\"id\": \"$dominio\", \"active\": true}"
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }
        }

        return conn.responseCode in 200..299
    }

    suspend fun crearPerfil(nombrePerfil: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/profiles")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-Api-Key", API_KEY) // [cite: 37]
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Cuerpo del JSON [cite: 88, 90]
            val jsonBody = JSONObject()
            jsonBody.put("name", nombrePerfil)
            // Puedes agregar configuraciones iniciales aquí si quieres,
            // por defecto NextDNS crea una config base.

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody.toString())
            writer.flush()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                // Estructura: { "data": { "id": "abc123" } } [cite: 222, 223, 224]
                val id = jsonResponse.getJSONObject("data").getString("id")
                Log.d("NextDNS", "Perfil creado con ID: $id")
                return@withContext id
            } else {
                Log.e("NextDNS", "Error creando perfil: $responseCode")
                // Leer error body si es necesario
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e("NextDNS", "Excepción: ${e.message}")
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }
}