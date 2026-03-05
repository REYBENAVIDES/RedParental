package com.example.redpaternal.presentacion.dashboard.home.fragmentos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentDispositivosBinding
import com.example.redpaternal.datos.modelo.RouterInfo
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.datos.remoto.AyudanteRed
import com.example.redpaternal.datos.remoto.AyudanteTPLink
import com.example.redpaternal.presentacion.adapters.RouterAdapter
import com.example.redpaternal.presentacion.dashboard.router.RouterDetalleActivity
import com.example.redpaternal.presentacion.router_configurar.RouterConfigurarActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class DispositivosFragment : Fragment() {

    private var _enlace: FragmentDispositivosBinding? = null
    private val enlace get() = _enlace!!
    private lateinit var adaptador: RouterAdapter
    private val listaRouters = mutableListOf<RouterInfo>()

    private lateinit var ayudanteBD: AyudanteBaseDatosFirebase
    private var listenerRegistro: ListenerRegistration? = null

    private val autenticacion = Firebase.auth

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        _enlace = FragmentDispositivosBinding.inflate(inflador, contenedor, false)
        ayudanteBD = AyudanteBaseDatosFirebase(requireContext())
        return enlace.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        configurarReciclador()
        cargarEncabezadoUsuario()
        iniciarEscuchaRouters()
        actualizarEstadoConexion()

        enlace.imgPerfilHome.setOnClickListener { irAFragmentoPerfil() }
        enlace.fabAgregar.setOnClickListener {
            startActivity(Intent(requireContext(), RouterConfigurarActivity::class.java))
        }
    }

    private fun configurarReciclador() {
        enlace.rvRouters.layoutManager = LinearLayoutManager(context)
        adaptador = RouterAdapter(listaRouters) { routerSeleccionado ->
            verificarYAcceder(routerSeleccionado)
        }
        enlace.rvRouters.adapter = adaptador
    }

    private fun verificarYAcceder(router: RouterInfo) {
        val redActual = AyudanteRed.obtenerRouterActual(requireContext())
        val estoyEnCasa = redActual != null && redActual.macRouter.equals(router.macRouter, ignoreCase = true)

        if (estoyEnCasa) {
            validarCredencialesLocales(router)
        } else {
            Toast.makeText(requireContext(), "Accediendo vía Nube ☁️", Toast.LENGTH_SHORT).show()
            abrirDetalle(router)
        }
    }

    private fun validarCredencialesLocales(router: RouterInfo) {
        lifecycleScope.launch {
            enlace.rvRouters.alpha = 0.5f
            enlace.rvRouters.isEnabled = false
            Toast.makeText(context, "Verificando acceso local...", Toast.LENGTH_SHORT).show()

            var clienteTemp: AyudanteTPLink? = null

            try {
                val ip = router.ipPuertaEnlace
                val pass = router.claveAdmin ?: ""

                if (pass.isEmpty()) throw Exception("Sin contraseña guardada.")

                clienteTemp = AyudanteTPLink(ip, pass)
                clienteTemp.autorizar()
                clienteTemp.cerrarSesion()

                abrirDetalle(router)

            } catch (e: Exception) {
                val msg = e.message ?: ""
                when {
                    msg.contains("408") -> Toast.makeText(context, "Router ocupado. Espera un momento.", Toast.LENGTH_LONG).show()
                    msg.contains("401") || msg.contains("Fallo") -> Toast.makeText(context, "Contraseña incorrecta. Reconfigura el router.", Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(context, "Error local: $msg", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (_enlace != null) {
                    enlace.rvRouters.alpha = 1.0f
                    enlace.rvRouters.isEnabled = true
                }
                try { clienteTemp?.cerrarSesion() } catch (e: Exception) {}
            }
        }
    }

    private fun abrirDetalle(router: RouterInfo) {
        val intento = Intent(requireContext(), RouterDetalleActivity::class.java)
        intento.putExtra("router_info", router)
        startActivity(intento)
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadoConexion()
    }

    private fun actualizarEstadoConexion() {
        val routerActual = AyudanteRed.obtenerRouterActual(requireContext())
        adaptador.macActualConectada = routerActual?.macRouter
        adaptador.notifyDataSetChanged()
    }

    private fun iniciarEscuchaRouters() {
        listenerRegistro?.remove()

        listenerRegistro = ayudanteBD.escucharRoutersEnTiempoReal(
            onDatos = { routers ->
                listaRouters.clear()
                listaRouters.addAll(routers)
                actualizarEstadoConexion()

                if (_enlace != null) {
                    enlace.lytEstadoVacio.visibility = if (listaRouters.isEmpty()) View.VISIBLE else View.GONE
                    enlace.rvRouters.visibility = if (listaRouters.isEmpty()) View.GONE else View.VISIBLE
                }
            },
            onError = { mensaje ->
                if (_enlace != null) {
                    Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun cargarEncabezadoUsuario() {
        val usuario = autenticacion.currentUser ?: return
        enlace.tvSaludo.text = "Hola, ${usuario.displayName ?: "Usuario"}"
        if (usuario.photoUrl != null) {
            Glide.with(this).load(usuario.photoUrl).circleCrop().into(enlace.imgPerfilHome)
        }
    }

    private fun irAFragmentoPerfil() {
        val fragmentoPerfil = PerfilUsuarioFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.contenedorFragmentos, fragmentoPerfil)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistro?.remove()
        _enlace = null
    }
}