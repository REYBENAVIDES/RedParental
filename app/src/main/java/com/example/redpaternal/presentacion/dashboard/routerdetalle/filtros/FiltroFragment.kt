package com.example.redpaternal.presentacion.dashboard.routerdetalle.filtros

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentFiltroBinding
import com.example.redpaternal.datos.modelo.FiltroSitio
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.presentacion.adapters.FiltroAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FiltroFragment : Fragment(R.layout.fragment_filtro) {

    private var _binding: FragmentFiltroBinding? = null
    private val binding get() = _binding!!

    private lateinit var ayudanteFirebase: AyudanteBaseDatosFirebase
    private lateinit var adaptador: FiltroAdapter
    private var listenerFiltros: ListenerRegistration? = null

    private var macRouterActual: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFiltroBinding.bind(view)
        ayudanteFirebase = AyudanteBaseDatosFirebase(requireContext())

        macRouterActual = arguments?.getString("macRouter") ?: ""

        if (macRouterActual.isEmpty()) {
            Toast.makeText(context, "Error: Router no identificado", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        configurarRecyclerView()
        configurarBotones()
        iniciarEscuchaFiltros()
    }

    private fun configurarRecyclerView() {
        adaptador = FiltroAdapter(
            listaFiltros = emptyList(),
            alClickeaEditar = { filtro -> irAPantallaEdicion(filtro) },
            alClickeaEliminar = { filtro -> confirmarEliminacion(filtro) },
            alCambiarEstado = { filtro, nuevoEstado -> cambiarEstadoFiltro(filtro, nuevoEstado) }
        )

        binding.rvFiltros.layoutManager = LinearLayoutManager(context)
        binding.rvFiltros.adapter = adaptador
        binding.rvFiltros.setHasFixedSize(true)
    }

    private fun configurarBotones() {
        binding.fabAgregarFiltro.setOnClickListener {
            irAPantallaEdicion(null)
        }
    }

    private fun iniciarEscuchaFiltros() {
        listenerFiltros?.remove()

        listenerFiltros = ayudanteFirebase.escucharFiltros(
            macRouter = macRouterActual,
            onDatos = { lista ->
                actualizarUI(lista)
            },
            onError = { mensaje ->
                if (_binding != null) {
                    Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun actualizarUI(lista: List<FiltroSitio>) {
        if (lista.isEmpty()) {
            mostrarEstadoVacio(true)
            binding.tvCantidadFiltros.text = "0"
            binding.tvSitiosBloqueados.text = "0"
        } else {
            mostrarEstadoVacio(false)
            adaptador.actualizarLista(lista)

            val filtrosActivos = lista.count { it.estaActivo }
            binding.tvCantidadFiltros.text = filtrosActivos.toString()

            val totalSitiosBloqueados = lista
                .filter { it.estaActivo && it.tipoAccion == "BLOQUEAR" }
                .flatMap { it.listaSitios }
                .distinct()
                .size
            binding.tvSitiosBloqueados.text = totalSitiosBloqueados.toString()
        }
    }

    private fun mostrarEstadoVacio(estaVacio: Boolean) {
        binding.layoutEstadoVacio.visibility = if (estaVacio) View.VISIBLE else View.GONE
        binding.rvFiltros.visibility = if (estaVacio) View.GONE else View.VISIBLE
    }

    private fun irAPantallaEdicion(filtro: FiltroSitio?) {
        val fragment = FiltroNuevoFragment()
        val args = Bundle()
        args.putString("macRouter", macRouterActual)
        if (filtro != null) {
            args.putSerializable("filtroEditar", filtro)
        }
        fragment.arguments = args

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.contenedorFragmentos, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun cambiarEstadoFiltro(filtro: FiltroSitio, nuevoEstado: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            ayudanteFirebase.cambiarEstadoFiltro(macRouterActual, filtro.id, nuevoEstado)
        }
    }

    private fun confirmarEliminacion(filtroAEliminar: FiltroSitio) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Eliminar filtro?")
            .setMessage("Se eliminará la regla '${filtroAEliminar.nombre}'.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val resultado = ayudanteFirebase.eliminarFiltro(macRouterActual, filtroAEliminar.id)

                    withContext(Dispatchers.Main) {
                        if (resultado is AyudanteBaseDatosFirebase.Resultado.Exito) {
                            Toast.makeText(context, "Filtro eliminado.", Toast.LENGTH_SHORT).show()
                        } else {
                            val msg = (resultado as AyudanteBaseDatosFirebase.Resultado.Error).mensaje
                            Toast.makeText(context, "Error al eliminar: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerFiltros?.remove()
        _binding = null
    }
}