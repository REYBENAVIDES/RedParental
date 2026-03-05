package com.example.redpaternal.datos.modelo

import java.util.Date

data class UsoActividad(
    val nombre: String,
    val url: String,
    val categoria: String,
    val tiempoMinutos: Int,
    val ultimaVez: Date = Date(),
    val esActivaAhora: Boolean = false,
    val esBloqueado: Boolean = false,
    val iconoRes: Int
)