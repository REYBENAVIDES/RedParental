package com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentMonitoreoDetalleBinding
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.presentacion.adapters.UsoActividadAdapter
import com.example.redpaternal.presentacion.dashboard.routerdetalle.viewmodel.RouterViewModel
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
        val itemBloquear = popup.menu.findItem(R.id.opc_bloquear) // Asegúrate de tener este ID en tu XML
        val activo = estaNotificacionActiva()

        // Obtenemos el estado actual del dispositivo
        val estaBloqueado = dispositivo.estaBloqueado

        itemNotificar.title = if (activo) "Desactivar seguimiento" else "Activar seguimiento"
        itemBloquear.title = if (estaBloqueado) "Desbloquear Internet" else "Bloquear Internet"

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
                        mostrarDialogoPersonalizado(
                            titulo = "Seguimiento en Vivo",
                            descripcion = "Al activar esta opción, verás una notificación fija con:",
                            icono1 = R.drawable.ic_reloj,
                            texto1 = "Sitio actual y tiempo de uso",
                            icono2 = R.drawable.ic_clave, // Usa el icono adecuado que tengas
                            texto2 = "Botones de bloqueo rápido",
                            textoBotonAccion = "Activar",
                            colorBotonAccion = R.color.primario
                        ) {
                            guardarEstadoNotificacion(true)
                            actualizarEstadoNotificacionUI()
                            notificarCambioAlServicio()
                        }
                    }
                    true
                }
                R.id.opc_bloquear -> {
                    if (estaBloqueado) {
                        mostrarDialogoPersonalizado(
                            titulo = "Desbloquear Acceso",
                            descripcion = "El dispositivo recuperará acceso total a Internet de forma inmediata.",
                            icono1 = R.drawable.ic_reloj, // Un ícono de éxito o similar
                            texto1 = "Conexión a la red restaurada",
                            icono2 = R.drawable.ic_reloj, // O cualquier ícono relevante
                            texto2 = "Los filtros por categoría seguirán activos",
                            textoBotonAccion = "Desbloquear",
                            colorBotonAccion = R.color.success
                        ) {
                            ejecutarBloqueo(false)
                        }
                    } else {
                        mostrarDialogoPersonalizado(
                            titulo = "Bloquear Acceso",
                            descripcion = "El dispositivo perderá conexión a Internet inmediatamente.",
                            icono1 = R.drawable.ic_reloj, // Un ícono de prohibido
                            texto1 = "Conexión a red denegada",
                            icono2 = R.drawable.ic_reloj, // O cualquier ícono de sin internet
                            texto2 = "Bloqueo estricto a nivel de router",
                            textoBotonAccion = "Bloquear",
                            colorBotonAccion = R.color.danger
                        ) {
                            ejecutarBloqueo(true)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun mostrarDialogoPersonalizado(
        titulo: String,
        descripcion: String,
        icono1: Int,
        texto1: String,
        icono2: Int,
        texto2: String,
        textoBotonAccion: String,
        colorBotonAccion: Int,
        accion: () -> Unit
    ) {
        val vistaDialogo = layoutInflater.inflate(R.layout.dialog_notificacion_config, null)
        val constructor = MaterialAlertDialogBuilder(requireContext())
            .setView(vistaDialogo)
            .setCancelable(true)

        val dialogo = constructor.create()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Referencias a las vistas del diálogo
        val tvTitulo = vistaDialogo.findViewById<android.widget.TextView>(R.id.tvTituloDialogo) // Asegúrate de agregar id en tu XML para los textos
        val tvDescripcion = vistaDialogo.findViewById<android.widget.TextView>(R.id.tvDescripcionDialogo)
        val ivItem1 = vistaDialogo.findViewById<android.widget.ImageView>(R.id.ivItem1Dialogo)
        val tvItem1 = vistaDialogo.findViewById<android.widget.TextView>(R.id.tvItem1Dialogo)
        val ivItem2 = vistaDialogo.findViewById<android.widget.ImageView>(R.id.ivItem2Dialogo)
        val tvItem2 = vistaDialogo.findViewById<android.widget.TextView>(R.id.tvItem2Dialogo)

        val btnAccion = vistaDialogo.findViewById<MaterialButton>(R.id.btnActivarDialogo)
        val btnCancelar = vistaDialogo.findViewById<MaterialButton>(R.id.btnCancelarDialogo)
        tvTitulo?.text = titulo
        tvDescripcion?.text = descripcion
        ivItem1?.setImageResource(icono1)
        tvItem1?.text = texto1
        ivItem2?.setImageResource(icono2)
        tvItem2?.text = texto2

        btnAccion.text = textoBotonAccion
        btnAccion.backgroundTintList = ContextCompat.getColorStateList(requireContext(), colorBotonAccion)

        btnAccion.setOnClickListener {
            accion()
            dialogo.dismiss()
        }

        btnCancelar.setOnClickListener {
            dialogo.dismiss()
        }

        dialogo.show()
    }

    private fun ejecutarBloqueo(bloquear: Boolean) {
        Toast.makeText(context, if (bloquear) "Procesando bloqueo..." else "Procesando desbloqueo...", Toast.LENGTH_SHORT).show()

        viewModel.cambiarEstadoBloqueoDispositivo(
            macRouter = macRouter,
            macDispositivo = dispositivo.macAddress,
            nombre = dispositivo.nombre,
            bloquear = bloquear
        ) { exito, mensaje ->
            if (exito) {
                dispositivo.estaBloqueado = bloquear
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error: $mensaje", Toast.LENGTH_LONG).show()
            }
        }
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