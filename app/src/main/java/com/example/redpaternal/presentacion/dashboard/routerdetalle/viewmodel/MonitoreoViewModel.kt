package com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.datos.modelo.SitioVisitado
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.datos.remoto.AyudanteTPLink
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class MonitoreoViewModel(application: Application) : AndroidViewModel(application) {

    private val ayudanteFirebase = AyudanteBaseDatosFirebase(application.applicationContext)

    private var dispositivoActual: Dispositivo? = null
    private var macRouterActual: String? = null
    private var perfilIdNextDNS: String? = null

    private var listenerRegistroSitios: ListenerRegistration? = null
    private var listenerRegistroDispositivos: ListenerRegistration? = null

    private val _listaDispositivos = MutableLiveData<List<Dispositivo>>()
    val listaDispositivos: LiveData<List<Dispositivo>> get() = _listaDispositivos

    private val _listaSitios = MutableLiveData<List<SitioVisitado>>()
    val listaSitios: LiveData<List<SitioVisitado>> get() = _listaSitios

    private val _tiempoTotalDia = MutableLiveData<String>()
    val tiempoTotalDia: LiveData<String> get() = _tiempoTotalDia

    private val _estadoVinculacion = MutableLiveData<Boolean>()
    val estadoVinculacion: LiveData<Boolean> get() = _estadoVinculacion

    private val _contadores = MutableLiveData<Pair<Int, Long>>()
    val contadores: LiveData<Pair<Int, Long>> get() = _contadores

    fun inicializarEscuchaDispositivos(macRouter: String) {
        if (macRouterActual == macRouter && listenerRegistroDispositivos != null) return

        macRouterActual = macRouter
        listenerRegistroDispositivos?.remove()

        listenerRegistroDispositivos = ayudanteFirebase.escucharDispositivos(
            macRouter = macRouter,
            onDatos = { lista ->
                _listaDispositivos.value = lista
            },
            onError = { error ->
                Log.e("MonitoreoVM", "Error escuchando dispositivos: $error")
            }
        )
    }

    fun preCargarDispositivo(dispositivo: Dispositivo, idNextDns: String?, macRouter: String) {
        val esMismoDispositivo = dispositivoActual?.macAddress == dispositivo.macAddress
        dispositivoActual = dispositivo
        macRouterActual = macRouter

        if (!idNextDns.isNullOrEmpty()) {
            perfilIdNextDNS = idNextDns
        }

        if (perfilIdNextDNS.isNullOrEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val idRecuperado = ayudanteFirebase.obtenerIdNextDnsPorMac(macRouter)
                if (idRecuperado != null) {
                    perfilIdNextDNS = idRecuperado
                }
            }
        }

        if (esMismoDispositivo && listenerRegistroSitios != null) return

        _listaSitios.value = emptyList()
        iniciarEscuchaSitios(macRouter, dispositivo.macAddress)

        viewModelScope.launch(Dispatchers.IO) {
            val (totalFiltros, totalBloqueos) = ayudanteFirebase.obtenerContadoresDispositivo(macRouter, dispositivo.macAddress)
            withContext(Dispatchers.Main) {
                _contadores.value = Pair(totalFiltros, totalBloqueos)
            }
        }
    }

    private fun iniciarEscuchaSitios(macRouter: String, macDispositivo: String) {
        listenerRegistroSitios?.remove()
        listenerRegistroSitios = ayudanteFirebase.escucharSitiosVisitados(
            macRouter = macRouter,
            macDispositivo = macDispositivo,
            ordenarPor = "tiempoTotalSegundos",
            onDatos = { lista ->
                procesarDatosSitios(lista)
            },
            onError = { error ->
                Log.e("MonitoreoVM", "Error escuchando sitios: $error")
            }
        )
    }

    private fun procesarDatosSitios(listaBruta: List<SitioVisitado>) {
        val listaProcesada = mutableListOf<SitioVisitado>()
        var segundosHoy = 0L
        val hoy = Calendar.getInstance()

        listaBruta.forEach { sitio ->
            val fecha = sitio.ultimaActualizacion?.toDate()
            val esDeHoy = fecha != null && esMismoDia(fecha, hoy.time)
            val segundosReales = if (esDeHoy) sitio.tiempoHoySegundos else 0L

            if (segundosReales > 0 || sitio.tiempoTotalSegundos > 0) {
                listaProcesada.add(sitio.copy(tiempoHoySegundos = segundosReales))
                segundosHoy += segundosReales
            }
        }

        val h = segundosHoy / 3600
        val m = (segundosHoy % 3600) / 60
        _tiempoTotalDia.value = "${h}h ${m}m"
        _listaSitios.value = listaProcesada
    }

    fun alternarBloqueoDeSitio(nombreSitio: String, urlSitio: String, bloquear: Boolean) {
        if (macRouterActual.isNullOrEmpty() || dispositivoActual == null) return
        viewModelScope.launch(Dispatchers.IO) {
            ayudanteFirebase.actualizarEstadoBloqueo(
                macRouterActual!!,
                dispositivoActual!!.macAddress,
                nombreSitio,
                bloquear
            )
        }
    }

    fun cambiarEstadoBloqueoDispositivo(macRouter: String, macDispositivo: String, nombre: String, bloquear: Boolean, onResultado: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("MonitoreoVM", "▶️ Iniciando proceso. Estado solicitado: Bloqueado=$bloquear")

                val credenciales = ayudanteFirebase.obtenerCredencialesRouter(macRouter)
                if (credenciales == null || credenciales.second.isEmpty()) {
                    throw Exception("No se encontraron credenciales en BD")
                }

                // Usamos la instancia Singleton unificada
                val ayudanteTplink = AyudanteTPLink.obtenerInstancia(credenciales.first, credenciales.second)

                Log.d("MonitoreoVM", "⏳ Enviando comando al router...")

                // 🌟 MIRA AQUÍ: Llamamos directamente a la función, ella sola gestiona la cola y la sesión
                if (bloquear) {
                    ayudanteTplink.bloquearDispositivoLocal(nombre, macDispositivo)
                } else {
                    ayudanteTplink.desbloquearDispositivoLocal(macDispositivo)
                }

                Log.d("MonitoreoVM", "✅ Hardware actualizado. Sincronizando con Firebase...")
                ayudanteFirebase.bloquearAccesoInternetDispositivo(macRouter, macDispositivo, bloquear)

                withContext(Dispatchers.Main) {
                    onResultado(true, if (bloquear) "Dispositivo bloqueado" else "Dispositivo desbloqueado")
                }
            } catch (e: Exception) {
                Log.e("MonitoreoVM", "❌ Error crítico: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResultado(false, "Error: ${e.message}")
                }
            }
        }
    }

    private fun esMismoDia(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
                c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistroSitios?.remove()
        listenerRegistroDispositivos?.remove()
    }
}