package com.example.redpaternal.presentacion.dashboard.routerdetalle.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.datos.modelo.RouterInfo
import com.example.redpaternal.datos.modelo.TipoDispositivo
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.datos.remoto.AyudanteRed
import com.example.redpaternal.datos.remoto.AyudanteTPLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class RouterViewModel(application: Application) : AndroidViewModel(application) {

    private val ayudanteFirebase = AyudanteBaseDatosFirebase(application.applicationContext)
    private var ayudanteTplink: AyudanteTPLink? = null

    private val _listaDispositivos = MutableLiveData<List<Dispositivo>>()
    val listaDispositivos: LiveData<List<Dispositivo>> = _listaDispositivos

    private val _estadoCarga = MutableLiveData<Boolean>()
    val estadoCarga: LiveData<Boolean> = _estadoCarga

    private val _modoConexion = MutableLiveData<String>()
    val modoConexion: LiveData<String> = _modoConexion

    private var macRouterObjetivo: String = ""
    private var listenerDispositivos: com.google.firebase.firestore.ListenerRegistration? = null
    private var jobOrquestador: Job? = null

    fun inicializar(info: RouterInfo) {
        if (macRouterObjetivo == info.macRouter) return

        macRouterObjetivo = info.macRouter
        ayudanteTplink = AyudanteTPLink(info.ipPuertaEnlace, info.claveAdmin ?: "")
        _estadoCarga.value = true

        iniciarLogicaConexion()
    }

    private fun iniciarLogicaConexion() {
        iniciarEscuchaNube()

        jobOrquestador?.cancel()
        jobOrquestador = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val contexto = getApplication<Application>()
                val routerRedActual = AyudanteRed.obtenerRouterActual(contexto)
                val estoyEnCasa = routerRedActual?.macRouter.equals(macRouterObjetivo, ignoreCase = true)

                if (estoyEnCasa) {
                    _modoConexion.postValue("Conexión Local")
                    ejecutarEscaneoLocal()
                } else {
                    _modoConexion.postValue("Modo Nube")
                }
                delay(10000L)
            }
        }
    }

    private fun iniciarEscuchaNube() {
        listenerDispositivos?.remove()
        listenerDispositivos = ayudanteFirebase.escucharDispositivos(
            macRouter = macRouterObjetivo,
            onDatos = { lista ->
                _listaDispositivos.postValue(lista)
                _estadoCarga.postValue(false)
            },
            onError = { _ -> }
        )
    }

    private suspend fun ejecutarEscaneoLocal() {
        try {
            val estadoRouter = ayudanteTplink?.obtenerEstadoCompleto() ?: return
            val dispositivosRaw = estadoRouter.dispositivos ?: emptyList()

            val dispositivosNuevos = dispositivosRaw.map { raw ->
                Dispositivo(
                    macAddress = raw.direccionMac.uppercase(),
                    nombre = raw.nombreHost,
                    ipAddress = raw.direccionIp,
                    estaConectado = raw.activo,
                    tipo = adivinarTipo(raw.nombreHost),
                    tiempoConectadoHoy = "0"
                )
            }
            ayudanteFirebase.guardarEstadoActualEnLote(macRouterObjetivo, dispositivosNuevos)
        } catch (e: Exception) { }
    }

    private fun adivinarTipo(nombre: String): TipoDispositivo {
        val n = nombre.lowercase(Locale.ROOT)
        return when {
            n.contains("android") || n.contains("phone") || n.contains("celular") -> TipoDispositivo.TELEFONO
            n.contains("win") || n.contains("pc") || n.contains("laptop") -> TipoDispositivo.COMPUTADORA
            else -> TipoDispositivo.DESCONOCIDO
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerDispositivos?.remove()
        jobOrquestador?.cancel()
    }
}