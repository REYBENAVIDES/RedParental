package com.example.redpaternal.presentacion.router_configurar.fragmentos

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.redpaternal.databinding.FragmentRouterCredencialesBinding
import com.example.redpaternal.datos.modelo.RouterInfo
import com.example.redpaternal.datos.remoto.AyudanteNextDNS
import com.example.redpaternal.datos.remoto.AyudanteTPLink
import com.example.redpaternal.presentacion.router_configurar.RouterConfigurarActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RouterCredencialesFragment : Fragment() {
    private var _binding: FragmentRouterCredencialesBinding? = null
    private val binding get() = _binding!!

    private lateinit var routerInfo: RouterInfo
    private val TAG = "RouterLogin"

    private val ayudanteNextDNS = AyudanteNextDNS()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRouterCredencialesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        routerInfo = arguments?.getSerializable("router_info") as? RouterInfo
            ?: run {
                Toast.makeText(context, "Error cargando datos", Toast.LENGTH_SHORT).show()
                return
            }

        binding.tvCredTitle.text = if (routerInfo.marca.isNotEmpty()) routerInfo.marca else "Router WiFi"
        binding.etPassword.hint = "Contraseña de admin para ${routerInfo.ipPuertaEnlace}"

        binding.btnConnect.setOnClickListener {
            val password = binding.etPassword.text.toString().trim()
            if (password.isEmpty()) {
                binding.tilPassword.error = "La contraseña es necesaria"
                return@setOnClickListener
            }
            binding.tilPassword.error = null
            validarYConectar(password)
        }
    }

    private fun validarYConectar(password: String) {
        binding.progressBarCred.visibility = View.VISIBLE
        binding.btnConnect.isEnabled = false
        binding.tilPassword.isEnabled = false

        Log.d(TAG, "Iniciando validación con IP: ${routerInfo.ipPuertaEnlace}")

        lifecycleScope.launch {
            var apiRouterLocal: AyudanteTPLink? = null

            try {
                apiRouterLocal = AyudanteTPLink(routerInfo.ipPuertaEnlace, password)
                apiRouterLocal.autorizar()

                val estado = apiRouterLocal.obtenerEstado()
                Log.d(TAG, "✅ Login Local Exitoso. Clientes: ${estado.totalClientes}")

                routerInfo.claveAdmin = password

                var perfilCreado = false
                var intentos = 0
                val maxIntentos = 3

                // Nombre descriptivo para el perfil en la nube
                val nombrePerfilNube = "RedPaternal - ${routerInfo.marca} (${routerInfo.nombreRed})"

                while (intentos < maxIntentos && !perfilCreado) {
                    intentos++
                    Log.d(TAG, "☁️ Intentando crear perfil nube... Intento $intentos/$maxIntentos")

                    val idPerfil = ayudanteNextDNS.crearPerfil(nombrePerfilNube)

                    if (idPerfil != null) {
                        routerInfo.nextDnsProfileId = idPerfil
                        perfilCreado = true
                        Log.d(TAG, "✅ Perfil Nube creado: $idPerfil")
                    } else {
                        Log.w(TAG, "⚠️ Falló intento $intentos en API NextDNS")
                        if (intentos < maxIntentos) {
                            delay(2000)
                        }
                    }
                }
                if (!perfilCreado) {
                    throw Exception("ERROR_SISTEMA_NUBE")
                }

                val activity = activity as? RouterConfigurarActivity
                activity?.simularConexionYGuardar(routerInfo)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en proceso: ${e.message}")

                if (isAdded) {
                    binding.progressBarCred.visibility = View.GONE
                    binding.btnConnect.isEnabled = true
                    binding.tilPassword.isEnabled = true

                    when {
                        e.message == "ERROR_SISTEMA_NUBE" -> {
                            binding.tilPassword.error = "Error de sistema (Servidores caídos). Intente más tarde."
                            Toast.makeText(context, "Verifique su conexión a internet", Toast.LENGTH_LONG).show()
                        }
                        e.message?.contains("408") == true -> {
                            binding.tilPassword.error = "Router saturado. Espera 1 min o reinícialo."
                        }
                        else -> {
                            binding.tilPassword.error = "Contraseña incorrecta"
                        }
                    }
                }

            } finally {
                Log.d(TAG, "🧹 Limpiando sesión en el router...")
                try {
                    apiRouterLocal?.cerrarSesion()
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo cerrar sesión local")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}