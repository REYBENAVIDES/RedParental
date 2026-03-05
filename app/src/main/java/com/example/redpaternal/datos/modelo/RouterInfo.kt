package com.example.redpaternal.datos.modelo

import java.io.Serializable
import com.google.firebase.firestore.Exclude // Importante para Firebase

data class RouterInfo(
    val nombreRed: String,
    val macRouter: String,
    val ipPuertaEnlace: String,
    val ipLocal: String,
    val frecuencia: Int,
    val intensidadSenal: Int,
    var modelo: String = "Cargando...",
    var marca: String = "Desconocida",
    var claveAdmin: String? = null,
    val numeroSerie: String? = null,
    var fechaConfiguracion: Long? = null,

    var nextDnsProfileId: String? = null
) : Serializable {

    @get:Exclude
    val nextDnsEndpoint: String?
        get() {
            return if (!nextDnsProfileId.isNullOrEmpty()) {
                "$nextDnsProfileId.dns.nextdns.io"
            } else {
                null
            }
        }

    fun obtenerDescripcion(): String {
        return "$nombreRed - $modelo ($ipPuertaEnlace)"
    }
}