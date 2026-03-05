package com.example.redpaternal.presentacion.dashboard.routerdetalle.filtros

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentFiltroNuevoBinding
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.datos.modelo.FiltroSitio
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class FiltroNuevoFragment : Fragment() {

    private var _binding: FragmentFiltroNuevoBinding? = null
    private val binding get() = _binding!!

    // Ayudantes
    // Ayudantes
    private lateinit var ayudanteFirebase: AyudanteBaseDatosFirebase

    // Estado y Datos
    private var macRouter: String = ""
    private var perfilIdNextDNS: String? = null
    private var filtroActual = FiltroSitio()
    private var esEdicion = false

    // Listas temporales
    private val listaSitiosAgregados = mutableListOf<String>()
    private val listaDispositivosSeleccionados = mutableListOf<String>()
    private val listaCategoriasSeleccionadas = mutableListOf<String>() // NUEVA LISTA
    private var listaTodosDispositivos: List<Dispositivo> = emptyList()

    private lateinit var adapterSitios: SitioSeleccionadoAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentFiltroNuevoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ayudanteFirebase = AyudanteBaseDatosFirebase(requireContext())

        recuperarArgumentos()
        cargarDatosIniciales()

        setupUIInicial()
        setupListeners()

        cargarDatosSiEsEdicion()
    }

    private fun recuperarArgumentos() {
        macRouter = arguments?.getString("macRouter") ?: ""
        val filtroObj = arguments?.getSerializable("filtroEditar") as? FiltroSitio

        if (filtroObj != null) {
            filtroActual = filtroObj
            esEdicion = true
            binding.tvTituloPagina.text = "Editar Regla"
            binding.btnCrearFiltro.text = "Actualizar Regla"
        }
    }

    private fun cargarDatosIniciales() {
        lifecycleScope.launch(Dispatchers.IO) {
            perfilIdNextDNS = ayudanteFirebase.obtenerIdNextDnsPorMac(macRouter)
            listaTodosDispositivos = ayudanteFirebase.obtenerDispositivosSoloLectura(macRouter)
        }
    }

    // --- CONFIGURACIÓN UI ---

    private fun setupUIInicial() {
        // RecyclerView Sitios
        adapterSitios = SitioSeleccionadoAdapter(listaSitiosAgregados) { sitio ->
            eliminarSitio(sitio)
        }
        binding.rvSitiosSeleccionados.layoutManager = LinearLayoutManager(context)
        binding.rvSitiosSeleccionados.adapter = adapterSitios

        // Generar Chips Estáticos
        generarChipsDias()
        generarChipsSugerencias()
    }

    private fun setupListeners() {
        binding.btnVolver.setOnClickListener { parentFragmentManager.popBackStack() }

        // --- TIPO DE FILTRO (Bloquear vs Permitir) ---
        binding.btnTipoBloquear.setOnClickListener {
            binding.radioBloquear.isChecked = true
            binding.radioPermitir.isChecked = false
            filtroActual.tipoAccion = "BLOQUEAR"
        }

        binding.btnTipoPermitir.setOnClickListener {
            binding.radioPermitir.isChecked = true
            binding.radioBloquear.isChecked = false
            filtroActual.tipoAccion = "PERMITIR"
        }

        // --- ALCANCE (Router vs Dispositivo) ---
        binding.btnAlcanceRouter.setOnClickListener {
            binding.radioRouter.isChecked = true
            binding.radioDispositivo.isChecked = false
            actualizarUIAlcance(false)
        }

        binding.btnAlcanceDispositivo.setOnClickListener {
            binding.radioDispositivo.isChecked = true
            binding.radioRouter.isChecked = false
            actualizarUIAlcance(true)
        }

        // --- SELECCIONAR DISPOSITIVOS ---
        binding.btnSeleccionarDispositivos.setOnClickListener {
            mostrarSelectorDispositivos()
        }

        // --- NUEVO: SELECCIONAR CATEGORÍAS ---
        binding.btnSeleccionarCategorias.setOnClickListener {
            mostrarSelectorCategorias()
        }

        // --- SITIOS WEB ---
        binding.etBuscarSitio.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                agregarSitioManual(v.text.toString())
                true
            } else false
        }

        // --- HORARIOS ---
        binding.switchSiempreActivo.setOnCheckedChangeListener { _, isChecked ->
            binding.containerHorarios.isVisible = !isChecked
            filtroActual.esSiempreActivo = isChecked
        }

        binding.btnHoraInicio.setOnClickListener { mostrarTimePicker(true) }
        binding.btnHoraFin.setOnClickListener { mostrarTimePicker(false) }

        // --- GUARDAR ---
        binding.btnCrearFiltro.setOnClickListener { guardar() }
    }

    private fun actualizarUIAlcance(esDispositivo: Boolean) {
        binding.containerDispositivosSeleccionados.isVisible = esDispositivo
        filtroActual.tipoAlcance = if (esDispositivo) "DISPOSITIVO" else "ROUTER"
    }

    // ==========================================
    //   LÓGICA DEL DIÁLOGO DE CATEGORÍAS
    // ==========================================
    private fun mostrarSelectorCategorias() {
        // Inflamos el XML del diálogo que proporcionaste
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_seleccionar_categorias, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Asignamos referencias a los elementos del diálogo usando IDs
        // NOTA: Como es un View inflado manualmente, usamos findViewById
        val btnPorn = dialogView.findViewById<LinearLayout>(R.id.btnCategoriaPornografia)
        val chkPorn = dialogView.findViewById<MaterialCheckBox>(R.id.checkPornografia)

        val btnApuestas = dialogView.findViewById<LinearLayout>(R.id.btnCategoriaApuestas)
        val chkApuestas = dialogView.findViewById<MaterialCheckBox>(R.id.checkApuestas)

        val btnCitas = dialogView.findViewById<LinearLayout>(R.id.btnCategoriaCitas)
        val chkCitas = dialogView.findViewById<MaterialCheckBox>(R.id.checkCitas)

        val btnPirateria = dialogView.findViewById<LinearLayout>(R.id.btnCategoriaPirateria)
        val chkPirateria = dialogView.findViewById<MaterialCheckBox>(R.id.checkPirateria)

        val btnRedes = dialogView.findViewById<LinearLayout>(R.id.btnCategoriaRedesSociales)
        val chkRedes = dialogView.findViewById<MaterialCheckBox>(R.id.checkRedesSociales)

        val btnJuegos = dialogView.findViewById<LinearLayout>(R.id.btnCategoriaJuegos)
        val chkJuegos = dialogView.findViewById<MaterialCheckBox>(R.id.checkJuegos)

        val btnVideo = dialogView.findViewById<LinearLayout>(R.id.btnCategoriaVideo)
        val chkVideo = dialogView.findViewById<MaterialCheckBox>(R.id.checkVideo)

        val btnConfirmar = dialogView.findViewById<View>(R.id.btnConfirmar)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelar)

        // Mapa auxiliar para iterar lógica
        // Clave: Nombre Categoría (Como se guarda en BD), Valor: CheckBox
        val mapaCategorias = mapOf(
            "Pornografía" to chkPorn,
            "Apuestas" to chkApuestas,
            "Citas" to chkCitas,
            "Piratería" to chkPirateria,
            "Redes sociales" to chkRedes,
            "Juegos en línea" to chkJuegos,
            "Vídeo bajo demanda" to chkVideo
        )

        // 1. Marcar los que ya estaban seleccionados
        mapaCategorias.forEach { (nombre, checkBox) ->
            checkBox.isChecked = listaCategoriasSeleccionadas.contains(nombre)
        }

        // 2. Configurar Clics en los Layouts (Contenedores) para marcar el Checkbox
        // Esto mejora la UX (clic en toda la tarjeta)
        fun toggleCheck(checkBox: MaterialCheckBox) { checkBox.isChecked = !checkBox.isChecked }

        btnPorn.setOnClickListener { toggleCheck(chkPorn) }
        btnApuestas.setOnClickListener { toggleCheck(chkApuestas) }
        btnCitas.setOnClickListener { toggleCheck(chkCitas) }
        btnPirateria.setOnClickListener { toggleCheck(chkPirateria) }
        btnRedes.setOnClickListener { toggleCheck(chkRedes) }
        btnJuegos.setOnClickListener { toggleCheck(chkJuegos) }
        btnVideo.setOnClickListener { toggleCheck(chkVideo) }

        // 3. Botones de Acción
        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnConfirmar.setOnClickListener {
            // Limpiar y rellenar lista principal
            listaCategoriasSeleccionadas.clear()
            mapaCategorias.forEach { (nombre, checkBox) ->
                if (checkBox.isChecked) {
                    listaCategoriasSeleccionadas.add(nombre)
                }
            }
            actualizarVistaCategorias()
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun actualizarVistaCategorias() {
        binding.chipGroupCategoriasSeleccionadas.removeAllViews()

        val hayCategorias = listaCategoriasSeleccionadas.isNotEmpty()

        // Actualizar textos resumen
        binding.tvResumenCategorias.text = if (hayCategorias)
            "Seleccionadas: ${listaCategoriasSeleccionadas.joinToString(", ")}"
        else
            "Ninguna categoría seleccionada"

        binding.tvTituloCategoriasSeleccionadas.isVisible = hayCategorias
        binding.chipGroupCategoriasSeleccionadas.isVisible = hayCategorias

        // Crear Chips visuales (Solo lectura / Click para borrar)
        listaCategoriasSeleccionadas.forEach { catNombre ->
            val chip = Chip(requireContext())
            chip.text = catNombre
            chip.isCloseIconVisible = true
            aplicarEstiloChip(chip)
            chip.isChecked = true // Para que salga con color primario

            // Al hacer clic en la X, se borra de la lista
            chip.setOnCloseIconClickListener {
                listaCategoriasSeleccionadas.remove(catNombre)
                actualizarVistaCategorias()
            }
            binding.chipGroupCategoriasSeleccionadas.addView(chip)
        }
    }


    // --- CHIPS (Estilos y Sugerencias) ---

    private fun aplicarEstiloChip(chip: Chip) {
        val colorPrimario = ContextCompat.getColor(requireContext(), R.color.primario)
        val colorBlanco = ContextCompat.getColor(requireContext(), R.color.white)
        val colorTextoGris = Color.parseColor("#7C797A")
        val colorBordeGris = Color.parseColor("#E0E0E0")

        val estados = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
        val coloresFondo = intArrayOf(colorPrimario, colorBlanco)
        val coloresTexto = intArrayOf(colorBlanco, colorTextoGris)
        val coloresBorde = intArrayOf(colorPrimario, colorBordeGris)

        chip.chipBackgroundColor = ColorStateList(estados, coloresFondo)
        chip.setTextColor(ColorStateList(estados, coloresTexto))
        chip.chipStrokeColor = ColorStateList(estados, coloresBorde)
        chip.chipStrokeWidth = 3f
        chip.isCheckedIconVisible = false
        chip.isCheckable = true
    }

    private fun generarChipsDias() {
        val dias = listOf("L", "M", "M", "J", "V", "S", "D")
        val valores = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

        binding.chipGroupDias.removeAllViews()
        dias.forEachIndexed { index, texto ->
            val chip = Chip(requireContext())
            chip.text = texto
            chip.tag = valores[index]
            aplicarEstiloChip(chip)
            binding.chipGroupDias.addView(chip)
        }
    }

    private fun generarChipsSugerencias() {
        val sugerencias = listOf("Facebook", "Instagram", "TikTok", "YouTube", "WhatsApp", "Roblox", "Netflix")
        binding.chipGroupSugerencias.removeAllViews()

        sugerencias.forEach { nombre ->
            val chip = Chip(requireContext())
            chip.text = nombre
            aplicarEstiloChip(chip)
            chip.setOnClickListener {
                if (chip.isChecked) agregarSitioDesdeChip(nombre) else eliminarSitio(nombre)
            }
            binding.chipGroupSugerencias.addView(chip)
        }
    }

    // --- GESTIÓN DE SITIOS ---
    private fun agregarSitioDesdeChip(sitio: String) {
        if (!listaSitiosAgregados.contains(sitio)) {
            listaSitiosAgregados.add(sitio)
            actualizarVistaSitios()
        }
    }

    private fun agregarSitioManual(sitio: String) {
        val sitioLimpio = sitio.trim()
        if (sitioLimpio.isNotBlank() && !listaSitiosAgregados.contains(sitioLimpio)) {
            listaSitiosAgregados.add(sitioLimpio)
            binding.etBuscarSitio.text?.clear()
            sincronizarChipsConLista()
            actualizarVistaSitios()
        }
    }

    private fun eliminarSitio(sitio: String) {
        listaSitiosAgregados.remove(sitio)
        sincronizarChipsConLista()
        actualizarVistaSitios()
    }

    private fun sincronizarChipsConLista() {
        binding.chipGroupSugerencias.children.forEach { view ->
            val chip = view as Chip
            chip.isChecked = listaSitiosAgregados.contains(chip.text.toString())
        }
    }

    private fun actualizarVistaSitios() {
        adapterSitios.notifyDataSetChanged()
        val hayDatos = listaSitiosAgregados.isNotEmpty()
        binding.rvSitiosSeleccionados.isVisible = hayDatos
        binding.tvTituloSeleccionados.isVisible = hayDatos
    }

    // --- SELECCIÓN DE DISPOSITIVOS ---
    private fun mostrarSelectorDispositivos() {
        lifecycleScope.launch {
            if (listaTodosDispositivos.isEmpty()) {
                listaTodosDispositivos = ayudanteFirebase.obtenerDispositivosSoloLectura(macRouter)
            }
            if (listaTodosDispositivos.isEmpty()) {
                Toast.makeText(context, "No hay dispositivos conectados.", Toast.LENGTH_LONG).show()
                return@launch
            }

            val nombres = listaTodosDispositivos.map { it.nombre }.toTypedArray()
            val preseleccionados = BooleanArray(listaTodosDispositivos.size) { i ->
                listaDispositivosSeleccionados.contains(listaTodosDispositivos[i].macAddress)
            }

            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Seleccionar Dispositivos")
                    .setMultiChoiceItems(nombres, preseleccionados) { _, which, isChecked ->
                        val mac = listaTodosDispositivos[which].macAddress
                        if (isChecked) {
                            if (!listaDispositivosSeleccionados.contains(mac)) listaDispositivosSeleccionados.add(mac)
                        } else {
                            listaDispositivosSeleccionados.remove(mac)
                        }
                    }
                    .setPositiveButton("Listo") { _, _ ->
                        val total = listaDispositivosSeleccionados.size
                        binding.tvResumenDispositivos.text = if(total > 0) "$total seleccionados" else "Ningún dispositivo seleccionado"
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    // --- HORARIOS ---
    private fun mostrarTimePicker(esInicio: Boolean) {
        val actual = if (esInicio) binding.tvHoraInicio.text.toString() else binding.tvHoraFin.text.toString()
        val partes = actual.split(":")
        val hora = partes.getOrElse(0) { "00" }.toInt()
        val min = partes.getOrElse(1) { "00" }.toInt()

        TimePickerDialog(requireContext(), { _, h, m ->
            val formato = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            if (esInicio) {
                binding.tvHoraInicio.text = formato
                filtroActual.horaInicio = formato
            } else {
                binding.tvHoraFin.text = formato
                filtroActual.horaFin = formato
            }
        }, hora, min, true).show()
    }

    // --- CARGAR DATOS (EDICIÓN) ---
    private fun cargarDatosSiEsEdicion() {
        if (!esEdicion) return

        binding.etNombreFiltro.setText(filtroActual.nombre)

        // Cargar Tipo (Bloquear/Permitir)
        if (filtroActual.tipoAccion == "PERMITIR") {
            binding.radioPermitir.isChecked = true
            binding.radioBloquear.isChecked = false
        } else {
            binding.radioBloquear.isChecked = true
            binding.radioPermitir.isChecked = false
        }

        // Cargar Alcance
        if (filtroActual.tipoAlcance == "ROUTER") {
            binding.radioRouter.isChecked = true
            binding.radioDispositivo.isChecked = false
            actualizarUIAlcance(false)
        } else {
            binding.radioDispositivo.isChecked = true
            binding.radioRouter.isChecked = false
            actualizarUIAlcance(true)
            listaDispositivosSeleccionados.addAll(filtroActual.listaDispositivos)
            binding.tvResumenDispositivos.text = "${listaDispositivosSeleccionados.size} seleccionados"
        }

        // Cargar Categorías
        listaCategoriasSeleccionadas.addAll(filtroActual.listaCategorias)
        actualizarVistaCategorias()

        // Cargar Sitios
        listaSitiosAgregados.addAll(filtroActual.listaSitios)
        sincronizarChipsConLista()
        actualizarVistaSitios()

        // Cargar Horarios
        binding.switchSiempreActivo.isChecked = filtroActual.esSiempreActivo
        if (!filtroActual.esSiempreActivo) {
            binding.tvHoraInicio.text = filtroActual.horaInicio
            binding.tvHoraFin.text = filtroActual.horaFin
            binding.chipGroupDias.children.forEach { v ->
                val chip = v as Chip
                val diaValor = chip.tag as Int
                chip.isChecked = filtroActual.diasSemana.contains(diaValor)
            }
        }
    }

    // --- GUARDAR ---
    private fun guardar() {
        val nombre = binding.etNombreFiltro.text.toString().trim()

        val sitiosLimpios = listaSitiosAgregados
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        // VALIDACIONES
        if (nombre.isEmpty()) { binding.etNombreFiltro.error = "Falta nombre"; return }

        // Validación Mixta: Debe haber AL MENOS un sitio O una categoría
        if (sitiosLimpios.isEmpty() && listaCategoriasSeleccionadas.isEmpty()) {
            Toast.makeText(context, "Selecciona al menos una categoría o agrega un sitio web", Toast.LENGTH_LONG).show()
            return
        }

        if (binding.radioDispositivo.isChecked && listaDispositivosSeleccionados.isEmpty()) {
            Toast.makeText(context, "Selecciona dispositivos afectados", Toast.LENGTH_SHORT).show()
            return
        }

        // CONSTRUIR OBJETO
        filtroActual.nombre = nombre
        filtroActual.listaSitios = sitiosLimpios
        filtroActual.listaCategorias = listaCategoriasSeleccionadas.toList() // NUEVO
        filtroActual.tipoAccion = if (binding.radioPermitir.isChecked) "PERMITIR" else "BLOQUEAR" // NUEVO
        filtroActual.listaDispositivos = if (binding.radioRouter.isChecked) emptyList() else listaDispositivosSeleccionados.toList()
        filtroActual.tipoAlcance = if (binding.radioRouter.isChecked) "ROUTER" else "DISPOSITIVO"

        if (!filtroActual.esSiempreActivo) {
            val dias = mutableListOf<Int>()
            binding.chipGroupDias.children.forEach { if ((it as Chip).isChecked) dias.add(it.tag as Int) }
            if (dias.isEmpty()) { Toast.makeText(context, "Selecciona un día", Toast.LENGTH_SHORT).show(); return }
            filtroActual.diasSemana = dias
        }

        // ENVIAR A FIREBASE
        binding.btnCrearFiltro.isEnabled = false
        binding.btnCrearFiltro.text = "Guardando..."

        lifecycleScope.launch(Dispatchers.IO) {
            val res = ayudanteFirebase.guardarFiltro(macRouter, filtroActual)

            withContext(Dispatchers.Main) {
                if (res is AyudanteBaseDatosFirebase.Resultado.Exito) {
                    Toast.makeText(context, "Regla guardada exitosamente", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    binding.btnCrearFiltro.isEnabled = true
                    binding.btnCrearFiltro.text = "Guardar Regla"
                    Toast.makeText(context, "Error al guardar. Verifique su conexión.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}