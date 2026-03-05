package com.example.redpaternal.presentacion.dashboard.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.redpaternal.R
import com.example.redpaternal.databinding.ActivityHomeBinding
import com.example.redpaternal.presentacion.dashboard.home.fragmentos.DispositivosFragment

class HomeActivity : AppCompatActivity() {

    private lateinit var enlace: ActivityHomeBinding

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        enlace = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(enlace.root)

        configurarBarraSistema()
        aplicarAjustesDePantalla()

        if (estadoGuardado == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedorFragmentos, DispositivosFragment())
                .commit()
        }
    }

    // ESTA FUNCIÓN SOLUCIONA QUE LOS BOTONES QUEDEN TAPADOS
    private fun aplicarAjustesDePantalla() {
        ViewCompat.setOnApplyWindowInsetsListener(enlace.root) { vista, insets ->
            val barrasSistema = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Esto aplica un margen interno (padding) igual al tamaño de las barras
            // Así el contenido nunca quedará por debajo de los botones de navegación
            vista.setPadding(barrasSistema.left, barrasSistema.top, barrasSistema.right, barrasSistema.bottom)

            insets
        }
    }

    private fun configurarBarraSistema() {
        window.navigationBarColor = getColor(R.color.fondo_1)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
    }
}