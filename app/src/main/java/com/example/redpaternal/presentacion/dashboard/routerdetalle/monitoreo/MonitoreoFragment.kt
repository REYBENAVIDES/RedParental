package com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentMonitoreoBinding
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.presentacion.adapters.DispositivoAdapter
import com.example.redpaternal.presentacion.dashboard.router.RouterDetalleActivity
import com.example.redpaternal.presentacion.dashboard.routerdetalle.viewmodel.RouterViewModel

class MonitoreoFragment : Fragment() {

    private var _enlace: FragmentMonitoreoBinding? = null
    private val enlace get() = _enlace!!

    private val viewModel: RouterViewModel by activityViewModels()

    private lateinit var adaptador: DispositivoAdapter
    private val listaDispositivos = mutableListOf<Dispositivo>()

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        _enlace = FragmentMonitoreoBinding.inflate(inflador, contenedor, false)
        return enlace.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        configurarReciclador()
        observarDatos()
    }

    private fun configurarReciclador() {
        enlace.rvDispositivos.layoutManager = LinearLayoutManager(requireContext())
        adaptador = DispositivoAdapter(listaDispositivos) { dispositivo ->
            (activity as? RouterDetalleActivity)?.irADetalleDispositivo(dispositivo)
        }
        enlace.rvDispositivos.adapter = adaptador
    }

    private fun observarDatos() {
        viewModel.listaDispositivos.observe(viewLifecycleOwner) { dispositivos ->
            listaDispositivos.clear()
            listaDispositivos.addAll(dispositivos)
            adaptador.notifyDataSetChanged()
            enlace.tvContadorActivos.text = "${dispositivos.size} dispositivos activos"
        }

        viewModel.estadoCarga.observe(viewLifecycleOwner) { cargando ->
            // Lógica de carga si es necesaria
        }

        viewModel.modoConexion.observe(viewLifecycleOwner) { modo ->
            actualizarInsigniaEstado(modo)
        }
    }

    private fun actualizarInsigniaEstado(modo: String) {
        val contexto = requireContext()
        val esLocal = modo.contains("Local", ignoreCase = true)
        val esRemoto = modo.contains("Remota", ignoreCase = true) || modo.contains("Nube", ignoreCase = true)

        if (esLocal) {
            enlace.tvTextoEstado.text = "Conexión Local"
            enlace.viewIndicadorEstado.backgroundTintList = ContextCompat.getColorStateList(contexto, R.color.success)
            enlace.lytEstadoConexion.alpha = 1.0f
        } else if (esRemoto) {
            enlace.tvTextoEstado.text = "Modo Nube"
            enlace.viewIndicadorEstado.backgroundTintList = ContextCompat.getColorStateList(contexto, R.color.warning)
            enlace.lytEstadoConexion.alpha = 0.9f
        } else {
            enlace.tvTextoEstado.text = "Desconectado"
            enlace.viewIndicadorEstado.backgroundTintList = ContextCompat.getColorStateList(contexto, android.R.color.holo_red_dark)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _enlace = null
    }
}