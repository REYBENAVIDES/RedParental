package com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentMonitoreoDetalleBinding
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.presentacion.adapters.UsoActividadAdapter
import com.example.redpaternal.servicios.MonitoreoService
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MonitoreoDetalleFragment : Fragment(R.layout.fragment_monitoreo_detalle) {

    private var _enlace: FragmentMonitoreoDetalleBinding? = null
    private val enlace get() = _enlace!!

    private val viewModel: MonitoreoViewModel by activityViewModels()
    private lateinit var dispositivo: Dispositivo
    private lateinit var adaptadorActividad: UsoActividadAdapter
    private var macRouter: String = ""

    companion object {
        private const val ARG_DISPOSITIVO = "arg_dispositivo"
        private const val ARG_MAC_ROUTER = "arg_mac_router"

        fun nuevaInstancia(dispositivo: Dispositivo, macRouter: String): MonitoreoDetalleFragment {
            val f = MonitoreoDetalleFragment()
            val args = Bundle()
            args.putSerializable(ARG_DISPOSITIVO, dispositivo)
            args.putString(ARG_MAC_ROUTER, macRouter)
            f.arguments = args
            return f
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _enlace = FragmentMonitoreoDetalleBinding.bind(view)

        dispositivo = arguments?.getSerializable(ARG_DISPOSITIVO) as? Dispositivo ?: return
        macRouter = arguments?.getString(ARG_MAC_ROUTER) ?: ""

        if (macRouter.isNotEmpty()) {
            viewModel.preCargarDispositivo(dispositivo, null, macRouter)
        } else {
            Toast.makeText(context, "Error: Router no identificado", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        setupRecyclerView()
        configurarUIEstatica()
        observarViewModel()
        actualizarEstadoNotificacionUI()
    }

    private fun setupRecyclerView() {
        enlace.rvActividad.layoutManager = LinearLayoutManager(context)
        adaptadorActividad = UsoActividadAdapter(emptyList(), 0) { sitio ->
            val fragmentoDetalle = MonitoreoSitioFragment.nuevaInstancia(
                nombre = sitio.nombre,
                url = sitio.url,
                tiempo = (sitio.tiempoTotalSegundos / 60).toInt(),
                esBloqueado = sitio.esBloqueado
            )
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.contenedorFragmentos, fragmentoDetalle)
                .addToBackStack(null)
                .commit()
        }
        enlace.rvActividad.adapter = adaptadorActividad
        enlace.rvActividad.setHasFixedSize(true)
    }

    private fun configurarUIEstatica() {
        enlace.tvNombreDispositivo.text = dispositivo.nombre
        enlace.tvMacDispositivo.text = dispositivo.macAddress
        enlace.btnVolver.setOnClickListener { parentFragmentManager.popBackStack() }
        enlace.btnMenu.setOnClickListener { mostrarMenuOpciones(it) }
    }

    private fun mostrarMenuOpciones(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.menu_detalle_dispositivo, popup.menu)

        val itemNotificar = popup.menu.findItem(R.id.opc_notificar)
        val activo = estaNotificacionActiva()

        itemNotificar.title = if (activo) "Desactivar seguimiento" else "Activar seguimiento"

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.opc_renombrar -> {
                    Toast.makeText(context, "Próximamente", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.opc_notificar -> {
                    if (activo) {
                        guardarEstadoNotificacion(false)
                        actualizarEstadoNotificacionUI()
                        notificarCambioAlServicio()
                    } else {
                        mostrarDialogoPersonalizado()
                    }
                    true
                }
                R.id.opc_bloquear -> {
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun mostrarDialogoPersonalizado() {
        val vistaDialogo = layoutInflater.inflate(R.layout.dialog_notificacion_config, null)
        val constructor = MaterialAlertDialogBuilder(requireContext())
            .setView(vistaDialogo)
            .setCancelable(true)

        val dialogo = constructor.create()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnActivar = vistaDialogo.findViewById<MaterialButton>(R.id.btnActivarDialogo)
        val btnCancelar = vistaDialogo.findViewById<MaterialButton>(R.id.btnCancelarDialogo)

        btnActivar.setOnClickListener {
            guardarEstadoNotificacion(true)
            actualizarEstadoNotificacionUI()
            notificarCambioAlServicio()
            dialogo.dismiss()
        }

        btnCancelar.setOnClickListener {
            dialogo.dismiss()
        }

        dialogo.show()
    }

    private fun estaNotificacionActiva(): Boolean {
        val prefs = requireContext().getSharedPreferences("monitoreo_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("activo_${dispositivo.macAddress}", true)
    }

    private fun guardarEstadoNotificacion(activa: Boolean) {
        val prefs = requireContext().getSharedPreferences("monitoreo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("activo_${dispositivo.macAddress}", activa).commit()
    }
    private fun actualizarEstadoNotificacionUI() {
        enlace.cardEstadoNotificacion.visibility = if (estaNotificacionActiva()) View.VISIBLE else View.GONE
    }

    private fun notificarCambioAlServicio() {
        val intent = Intent(requireContext(), MonitoreoService::class.java).apply {
            action = MonitoreoService.ACCION_INICIAR_GLOBAL
            putExtra(MonitoreoService.EXTRA_MAC_ROUTER, macRouter)
        }
        requireContext().startService(intent)
    }

    private fun observarViewModel() {
        viewModel.listaSitios.observe(viewLifecycleOwner) { lista ->
            val totalSegundosHoy = lista.sumOf { it.tiempoHoySegundos }
            val totalMinutosVisual = (totalSegundosHoy / 60).toInt()
            adaptadorActividad.actualizarDatos(lista, totalMinutosVisual)
        }

        viewModel.tiempoTotalDia.observe(viewLifecycleOwner) { tiempoTexto ->
            enlace.tvTiempoTotal.text = tiempoTexto ?: "0h 0m"
        }

        viewModel.estadoVinculacion.observe(viewLifecycleOwner) { estaVinculado ->
            enlace.cardAlertaDns.visibility = if (estaVinculado) View.GONE else View.VISIBLE
        }

        viewModel.contadores.observe(viewLifecycleOwner) { (totalFiltros, totalBloqueos) ->
            enlace.tvTotalFiltros.text = totalFiltros.toString()
            enlace.tvTotalBloqueos.text = totalBloqueos.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _enlace = null
    }
}