package com.example.redpaternal

import android.app.Application
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        // Configurar Firestore para desarrollo
        val db = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)  // Habilitar cache offline
            .build()
        db.firestoreSettings = settings

        // Configurar logging (opcional para desarrollo)
        if (BuildConfig.DEBUG) {
            // FirebaseFirestore.setLoggingEnabled(true)
        }
    }
}