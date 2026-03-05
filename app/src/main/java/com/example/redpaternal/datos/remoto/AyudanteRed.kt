package com.example.redpaternal.datos.remoto

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.example.redpaternal.datos.modelo.RouterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.URL

object AyudanteRed {
    private const val ETIQUETA = "AyudanteRed"

    // 1. OBTENER INFORMACIÓN REAL DEL ROUTER CONECTADO
    fun obtenerRouterActual(contexto: Context): RouterInfo? {
        try {
            val cm = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wm = contexto.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val redActiva: Network? = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(redActiva)

            // Verificar conexión WiFi
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val linkProps: LinkProperties? = cm.getLinkProperties(redActiva)
                val infoWifi = wm.connectionInfo

                // Obtener IPs
                val miIp = linkProps?.linkAddresses?.find { it.address is Inet4Address }?.address?.hostAddress ?: "0.0.0.0"

                // Obtener Gateway (IP del Router TP-Link)
                val gatewayIp = linkProps?.routes?.find {
                    it.gateway is Inet4Address && !it.gateway?.isAnyLocalAddress!!
                }?.gateway?.hostAddress ?: "192.168.0.1" // Fallback común TP-Link

                // Datos Técnicos
                val ssid = infoWifi.ssid.replace("\"", "") // Limpiar comillas
                val bssid = infoWifi.bssid ?: "02:00:00:00:00:00" // MAC Address
                val frecuencia = infoWifi.frequency
                val rssi = infoWifi.rssi

                return RouterInfo(
                    nombreRed = ssid,
                    macRouter = bssid,
                    ipPuertaEnlace = gatewayIp,
                    ipLocal = miIp,
                    frecuencia = frecuencia,
                    intensidadSenal = rssi,
                    modelo = "Detectando...",
                    marca = "Desconocida",
                )
            }
        } catch (e: Exception) {
            Log.e(ETIQUETA, "Error obteniendo info WiFi", e)
        }
        return null
    }

    // 2. OBTENER FABRICANTE (Para saber si es TP-Link)
    suspend fun obtenerFabricante(mac: String): String = withContext(Dispatchers.IO) {
        if (mac.startsWith("02:00") || mac.isEmpty()) return@withContext "Desconocido (Restricción Android)"

        try {
            // API pública simple para obtener fabricante por MAC
            val url = URL("https://api.macvendors.com/$mac")
            url.readText()
        } catch (e: Exception) {
            "Generico"
        }
    }
}