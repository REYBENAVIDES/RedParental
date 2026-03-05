package com.example.redpaternal.presentacion.dashboard.router

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.redpaternal.R
import com.example.redpaternal.databinding.ActivityRouterDetalleBinding
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.datos.modelo.RouterInfo
import com.example.redpaternal.presentacion.dashboard.routerdetalle.filtros.FiltroFragment
import com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo.MonitoreoDetalleFragment
import com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo.MonitoreoFragment
import com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo.MonitoreoViewModel
import com.example.redpaternal.presentacion.dashboard.routerdetalle.perfil.PerfilFragment
import com.example.redpaternal.presentacion.dashboard.routerdetalle.viewmodel.RouterViewModel
import com.example.redpaternal.servicios.MonitoreoService

class RouterDetalleActivity : AppCompatActivity() {

    private lateinit var enlace: ActivityRouterDetalleBinding
    private val routerViewModel: RouterViewModel by viewModels()
    private val monitoreoViewModel: MonitoreoViewModel by viewModels()

    private var idNextDnsActual: String? = null
    private var macRouterActual: String? = null

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        enlace = ActivityRouterDetalleBinding.inflate(layoutInflater)
        setContentView(enlace.root)

        val info = intent.getSerializableExtra("router_info") as? RouterInfo
        if (info == null) {
            Toast.makeText(this, "Error de datos", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        idNextDnsActual = info.nextDnsProfileId
        macRouterActual = info.macRouter

        routerViewModel.inicializar(info)

        monitoreoViewModel.inicializarEscuchaDispositivos(macRouterActual!!)

        monitoreoViewModel.listaDispositivos.observe(this) { lista ->
            if (lista.isNotEmpty()) {
                inicializarPreferenciasPorDefecto(lista)
            }
        }

        configurarBarraSistema()
        configurarNavegacionInferior()

        if (estadoGuardado == null) {
            cambiarFragmento(MonitoreoFragment())
            enlace.navegacionInferior.selectedItemId = R.id.nav_monitoreo
        }
    }

    private fun inicializarPreferenciasPorDefecto(lista: List<Dispositivo>) {
        val prefs = getSharedPreferences("monitoreo_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var huboCambios = false

        lista.forEach { dispositivo ->
            val llave = "activo_${dispositivo.macAddress}"
            if (!prefs.contains(llave)) {
                editor.putBoolean(llave, true)
                huboCambios = true
            }
        }

        if (huboCambios) editor.commit() // commit es síncrono, asegura que el servicio lea bien los datos
        iniciarServicioSupervision()
    }

    private fun iniciarServicioSupervision() {
        if (macRouterActual.isNullOrEmpty()) return

        val intentServicio = Intent(this, MonitoreoService::class.java).apply {
            action = MonitoreoService.ACCION_INICIAR_GLOBAL
            putExtra(MonitoreoService.EXTRA_MAC_ROUTER, macRouterActual)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intentServicio)
        } else {
            startService(intentServicio)
        }
    }

    private fun configurarNavegacionInferior() {
        enlace.navegacionInferior.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitoreo -> {
                    cambiarFragmento(MonitoreoFragment())
                    true
                }
                R.id.nav_filtro -> {
                    val fragmento = FiltroFragment()
                    val args = Bundle()
                    args.putString("macRouter", macRouterActual)
                    fragmento.arguments = args
                    cambiarFragmento(fragmento)
                    true
                }
                R.id.nav_ajustes -> {
                    cambiarFragmento(PerfilFragment())
                    true
                }
                else -> false
            }
        }
        enlace.navegacionInferior.setOnItemReselectedListener { }
    }

    private fun cambiarFragmento(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.contenedorFragmentos, f)
            .commit()
    }

    fun irADetalleDispositivo(dispositivo: Dispositivo) {
        if (macRouterActual.isNullOrEmpty()) return
        monitoreoViewModel.preCargarDispositivo(dispositivo, idNextDnsActual, macRouterActual!!)
        val fragmento = MonitoreoDetalleFragment.nuevaInstancia(dispositivo, macRouterActual!!)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.contenedorFragmentos, fragmento)
            .addToBackStack(null)
            .commit()
    }

    private fun configurarBarraSistema() {
        window.navigationBarColor = getColor(R.color.fondo_1)
        val controlador = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controlador.isAppearanceLightNavigationBars = true
    }
}