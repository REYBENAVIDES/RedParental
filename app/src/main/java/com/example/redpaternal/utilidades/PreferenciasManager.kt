package com.example.redpaternal.utilidades

import android.content.Context
import android.content.SharedPreferences

class PreferenciasManager(private val contexto: Context) {
    private val preferencias: SharedPreferences = contexto.getSharedPreferences("preferencias_app", Context.MODE_PRIVATE)

    companion object {
        // Claves en español
        private const val CLAVE_ES_PRIMERA_VEZ = "es_primera_vez"
    }

    fun esPrimeraVez(): Boolean {
        return preferencias.getBoolean(CLAVE_ES_PRIMERA_VEZ, true)
    }

    fun marcarPrimeraVezCompletada() {
        preferencias.edit().putBoolean(CLAVE_ES_PRIMERA_VEZ, false).apply()
    }
}