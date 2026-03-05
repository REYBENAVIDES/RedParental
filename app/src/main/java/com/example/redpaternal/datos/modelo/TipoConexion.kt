package com.example.redpaternal.datos.modelo

enum class TipoConexion(val valor: String) {
    ANFITRION_2G("host_2g"),
    ANFITRION_5G("host_5g"),
    ANFITRION_6G("host_6g"),
    INVITADO_2G("guest_2g"),
    INVITADO_5G("guest_5g"),
    INVITADO_6G("guest_6g"),
    IOT_2G("iot_2g"),
    IOT_5G("iot_5g"),
    IOT_6G("iot_6g"),
    CABLEADO("wired"),
    DESCONOCIDO("unknown");

    companion object {
        fun desdeEntero(tipo: Int): TipoConexion {
            return when(tipo) {
                0 -> CABLEADO
                1 -> ANFITRION_2G
                2 -> INVITADO_2G
                3 -> ANFITRION_5G
                4 -> INVITADO_5G
                13 -> IOT_2G
                14 -> IOT_5G
                else -> DESCONOCIDO
            }
        }
    }
}