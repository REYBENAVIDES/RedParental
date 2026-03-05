package com.example.redpaternal.datos.modelo

import java.io.Serializable
import com.google.firebase.Timestamp
import java.util.Date

data class Dispositivo(
    val macAddress: String = "",
    val nombre: String = "",
    val ipAddress: String = "",
    val fabricante: String = "",

    // Estado
    val estaConectado: Boolean = false,
    val estaBloqueado: Boolean = false,

    val fechaUltimaConexion: Date? = null,
    val fechaRegistro: Date? = null,

    val tipo: TipoDispositivo = TipoDispositivo.DESCONOCIDO,
    val tiempoConectadoHoy: String = "",
    val ultimaActualizacionSitios: Date? = null
) : Serializable

enum class TipoDispositivo {
    TELEFONO,
    COMPUTADORA,
    TABLET,
    TV,
    CONSOLA,
    CAMARA,
    IOT,
    DESCONOCIDO
}