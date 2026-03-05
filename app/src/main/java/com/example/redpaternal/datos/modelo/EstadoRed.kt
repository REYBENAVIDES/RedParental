package com.example.redpaternal.datos.modelo

data class EstadoRed(
    val estado: String,
    val dispositivosConectados: Int,
    val velocidadSubida: Double,
    val velocidadBajada: Double,
    val uptime: Int,
    val mensajeError: String? = null
)