package com.example.redpaternal.datos.modelo

import com.google.firebase.Timestamp
import java.io.Serializable

data class FiltroSitio(
    var id: String = "",
    var nombre: String = "",
    var tipoAlcance: String = "ROUTER",

    var tipoAccion: String = "BLOQUEAR",
    var listaCategorias: List<String> = emptyList(),

    var listaDispositivos: List<String> = emptyList(),
    var listaSitios: List<String> = emptyList(),
    var esSiempreActivo: Boolean = false,
    var horaInicio: String = "00:00",
    var horaFin: String = "00:00",
    var diasSemana: List<Int> = emptyList(),
    var estaActivo: Boolean = true,
    var fechaCreacion: Timestamp? = null
) : Serializable