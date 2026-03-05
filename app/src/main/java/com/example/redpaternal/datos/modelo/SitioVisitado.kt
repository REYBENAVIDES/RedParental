package com.example.redpaternal.datos.modelo

import com.google.firebase.Timestamp

data class SitioVisitado(
    val url: String = "",
    val nombre: String = "",
    val categoria: String = "General",
    val esBloqueado: Boolean = false,

    val tiempoTotalSegundos: Long = 0,
    val tiempoHoySegundos: Long = 0,
    val ultimaActualizacion: Timestamp? = null,
    val iconoUrl: String? = null
)