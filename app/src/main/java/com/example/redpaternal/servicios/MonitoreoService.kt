package com.example.redpaternal.servicios

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.redpaternal.R
import com.example.redpaternal.datos.modelo.SitioVisitado
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.presentacion.dashboard.home.HomeActivity
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import java.util.Date

class MonitoreoService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var ayudanteFirebase: AyudanteBaseDatosFirebase
    private val listenersActivos = mutableMapOf<String, ListenerRegistration>()
    private var macRouterActual: String = ""

    companion object {
        const val CANAL_ID = "monitoreo_global"
        const val GRUPO_KEY = "com.example.redpaternal.MONITOREO_GROUP"
        const val SUMMARY_ID = 100
        const val ACCION_INICIAR_GLOBAL = "ACCION_INICIAR_GLOBAL"
        const val ACCION_BLOQUEAR_SITIO = "ACCION_BLOQUEAR_SITIO"
        const val EXTRA_MAC_ROUTER = "extra_mac_router"
        const val EXTRA_MAC_DISPOSITIVO = "extra_mac_dispositivo"
        const val EXTRA_NOMBRE_SITIO = "extra_nombre_sitio"
        const val EXTRA_ES_BLOQUEADO = "extra_es_bloqueado"
    }

    private var contadorIdsNotificacion = 101
    private val mapaIdsDispositivos = mutableMapOf<String, Int>()

    private fun obtenerIdNotificacion(mac: String): Int {
        return mapaIdsDispositivos.getOrPut(mac) { contadorIdsNotificacion++ }
    }

    private val cacheUltimoSitio = mutableMapOf<String, Pair<String, SitioVisitado?>>()

    override fun onCreate() {
        super.onCreate()
        ayudanteFirebase = AyudanteBaseDatosFirebase(this)
        crearCanalNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_INICIAR_GLOBAL -> {
                macRouterActual = intent.getStringExtra(EXTRA_MAC_ROUTER) ?: ""
                iniciarMonitoreoGlobal()
            }
            ACCION_BLOQUEAR_SITIO -> manejarAccionBloqueo(intent)
        }
        return START_STICKY
    }

    private fun iniciarMonitoreoGlobal() {
        mostrarNotificacionResumen("Red Paternal", "Supervisión activa")

        ayudanteFirebase.escucharDispositivos(macRouterActual, onDatos = { listaDispositivos ->
            listaDispositivos.forEach { dispositivo ->
                if (!listenersActivos.containsKey(dispositivo.macAddress)) {
                    val registro = ayudanteFirebase.escucharSitiosVisitados(
                        macRouter = macRouterActual,
                        macDispositivo = dispositivo.macAddress,
                        ordenarPor = "ultimaActualizacion",
                        onDatos = { sitios ->
                            val ultimo = sitios.firstOrNull()
                            cacheUltimoSitio[dispositivo.macAddress] = Pair(dispositivo.nombre, ultimo)
                            actualizarNotificacionDispositivo(dispositivo.nombre, dispositivo.macAddress, ultimo)
                        },
                        onError = {}
                    )
                    registro?.let { listenersActivos[dispositivo.macAddress] = it }
                } else {
                    cacheUltimoSitio[dispositivo.macAddress]?.let { (nombre, sitio) ->
                        actualizarNotificacionDispositivo(nombre, dispositivo.macAddress, sitio)
                    }
                }
            }
        }, onError = {})
    }

    private fun actualizarNotificacionDispositivo(nombre: String, mac: String, sitio: SitioVisitado?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = getSharedPreferences("monitoreo_prefs", Context.MODE_PRIVATE)
        val estaActivado = prefs.getBoolean("activo_$mac", true)

        val notifId = obtenerIdNotificacion(mac)

        if (!estaActivado) {
            manager.cancel(notifId)
            return
        }

        val avatar = obtenerBitmapCircular(R.drawable.img_telefono)

        // Valores por defecto si aún no hay historial
        val nombreSitio = sitio?.nombre ?: "Sin actividad reciente"
        val tiempoMin = (sitio?.tiempoHoySegundos ?: 0) / 60
        val esBloqueado = sitio?.esBloqueado ?: false
        val tiempoAct = sitio?.ultimaActualizacion?.toDate()?.time ?: System.currentTimeMillis()

        val iconoEstado = if (esBloqueado) "🚫 " else ""
        val contenido = if (sitio != null) "En: $iconoEstado$nombreSitio  •  $tiempoMin min" else "Esperando actividad..."

        val builder = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.img_logo)
            .setLargeIcon(avatar)
            .setContentTitle(nombre)
            .setContentText(contenido)
            .setGroup(GRUPO_KEY)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(tiempoAct)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contenido))

        // Si hay un sitio, agregamos el botón de bloquear/desbloquear
        if (sitio != null) {
            val textoBoton = if (esBloqueado) "Desbloquear" else "Bloquear Sitio"
            val colorBotonStr = if (esBloqueado) "#2196F3" else "#F44336"

            val intentBloqueo = Intent(this, MonitoreoService::class.java).apply {
                action = ACCION_BLOQUEAR_SITIO
                putExtra(EXTRA_MAC_ROUTER, macRouterActual)
                putExtra(EXTRA_MAC_DISPOSITIVO, mac)
                putExtra(EXTRA_NOMBRE_SITIO, nombreSitio)
                putExtra(EXTRA_ES_BLOQUEADO, !esBloqueado)
            }

            val pIntent = PendingIntent.getService(
                this, notifId, intentBloqueo,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setColor(Color.parseColor(colorBotonStr))
            builder.addAction(NotificationCompat.Action.Builder(0, textoBoton, pIntent).build())
        }

        manager.notify(notifId, builder.build())
        actualizarNotificacionResumen("Red Paternal", "${listenersActivos.size} dispositivos en supervisión")
    }

    private fun mostrarNotificacionResumen(titulo: String, texto: String) {
        val notification = NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setSmallIcon(R.drawable.img_logo)
            .setGroup(GRUPO_KEY)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(SUMMARY_ID, notification)
    }

    private fun actualizarNotificacionResumen(titulo: String, texto: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val summary = NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setSmallIcon(R.drawable.img_logo)
            .setGroup(GRUPO_KEY)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    private fun manejarAccionBloqueo(intent: Intent) {
        val macR = intent.getStringExtra(EXTRA_MAC_ROUTER) ?: ""
        val macD = intent.getStringExtra(EXTRA_MAC_DISPOSITIVO) ?: ""
        val sitio = intent.getStringExtra(EXTRA_NOMBRE_SITIO) ?: ""
        val nuevoEstado = intent.getBooleanExtra(EXTRA_ES_BLOQUEADO, false)
        scope.launch { ayudanteFirebase.actualizarEstadoBloqueo(macR, macD, sitio, nuevoEstado) }
    }

    private fun obtenerBitmapCircular(resId: Int): Bitmap {
        val src = BitmapFactory.decodeResource(resources, resId)
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, src.width, src.height)
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(src.width / 2f, src.height / 2f, src.width / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, rect, rect, paint)
        return output
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_ID, "Monitoreo Global", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenersActivos.values.forEach { it.remove() }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}