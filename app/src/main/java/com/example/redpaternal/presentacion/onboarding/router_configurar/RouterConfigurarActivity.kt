package com.example.redpaternal.presentacion.router_configurar

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.redpaternal.R
import com.example.redpaternal.databinding.ActivityRouterConfigurarBinding
import com.example.redpaternal.datos.modelo.RouterInfo
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.presentacion.router_configurar.fragmentos.ConexionExitosaFragment
import com.example.redpaternal.presentacion.router_configurar.fragmentos.InstruccionesFragment
import com.example.redpaternal.presentacion.router_configurar.fragmentos.RouterCredencialesFragment
import com.example.redpaternal.presentacion.router_configurar.fragmentos.RouterScanFragment
import kotlinx.coroutines.launch

class RouterConfigurarActivity : AppCompatActivity() {
    private lateinit var enlace: ActivityRouterConfigurarBinding
    private lateinit var repositorio: AyudanteBaseDatosFirebase
    private var infoRouter: RouterInfo? = null

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        supportActionBar?.hide()
        configurarBarraNavegacion()

        enlace = ActivityRouterConfigurarBinding.inflate(layoutInflater)
        setContentView(enlace.root)

        repositorio = AyudanteBaseDatosFirebase(this)

        if (repositorio.obtenerUsuarioActual() == null) {
            finish()
            return
        }

        if (estadoGuardado == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, InstruccionesFragment())
                .commit()
        }
    }

    fun simularConexionYGuardar(routerCompleto: RouterInfo) {
        this.infoRouter = routerCompleto

        lifecycleScope.launch {
            val resultado = repositorio.guardarRouter(routerCompleto)

            when (resultado) {
                is AyudanteBaseDatosFirebase.Resultado.Exito -> {
                    Log.d("ConfigRouter", "✅ Guardado exitoso")
                    mostrarFragmentoExito()
                }
                is AyudanteBaseDatosFirebase.Resultado.Error -> {
                    Log.e("ConfigRouter", "❌ Error: ${resultado.mensaje}")
                    Toast.makeText(this@RouterConfigurarActivity, "Error al guardar. Verifique su conexión.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun mostrarFragmentoExito() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ConexionExitosaFragment())
            .commit()
    }

    private fun configurarBarraNavegacion() {
        window.navigationBarColor = getColor(R.color.fondo_1)
        val controlador = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controlador.isAppearanceLightNavigationBars = true
    }

    private fun mostrarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragmento)
            .addToBackStack(null)
            .commit()
    }

    fun mostrarFragmentoEscaneo() {
        mostrarFragmento(RouterScanFragment())
    }

    fun mostrarFragmentoCredenciales(router: RouterInfo) {
        this.infoRouter = router
        val fragmentoCredenciales = RouterCredencialesFragment().apply {
            arguments = Bundle().apply { putSerializable("router_info", router) }
        }
        mostrarFragmento(fragmentoCredenciales)
    }
}