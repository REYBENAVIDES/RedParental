package com.example.redpaternal.datos.modelo

data class Firmware(
    val versionHardware: String,
    val modelo: String,
    val versionFirmware: String
)

data class DispositivoTplink(
    val tipo: TipoConexion,
    val direccionMac: String,
    val direccionIp: String,
    val nombreHost: String,
    val activo: Boolean,
    val velocidadBajada: Long = 0L,
    val velocidadSubida: Long = 0L,
    val paquetesEnviados: Long = 0L,
    val paquetesRecibidos: Long = 0L
)

data class EstadoRouter(
    val macWan: String = "",
    val macLan: String = "",
    val ipWan: String = "",
    val ipLan: String = "",
    val ipPuertaEnlace: String = "",
    val tiempoActivo: Long = 0L,
    val dispositivos: List<DispositivoTplink> = emptyList(),

    val wifi2gHabilitado: Boolean = false,
    val wifi5gHabilitado: Boolean = false,
    val invitados2gHabilitado: Boolean = false,
    val invitados5gHabilitado: Boolean = false,

    val totalClientes: Int = 0
)