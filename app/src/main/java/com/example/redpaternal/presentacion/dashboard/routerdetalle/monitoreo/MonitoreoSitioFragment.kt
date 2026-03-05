package com.example.redpaternal.presentacion.dashboard.routerdetalle.monitoreo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentMonitoreoSitioBinding
import com.example.redpaternal.datos.modelo.FiltroSitio
import com.example.redpaternal.presentacion.adapters.FiltroSitioAdapter
import com.google.android.material.snackbar.Snackbar

class MonitoreoSitioFragment : Fragment() {

    private var _enlace: FragmentMonitoreoSitioBinding? = null
    private val enlace get() = _enlace!!

    private val viewModel: MonitoreoViewModel by activityViewModels()

    private var nombreSitio: String = ""
    private var urlSitio: String = ""
    private var tiempoSitio: Int = 0
    private var esBloqueado: Boolean = false

    private val listaFiltros = mutableListOf<FiltroSitio>()
    private lateinit var adaptador: FiltroSitioAdapter

    companion object {
        private const val ARG_NOMBRE = "arg_nombre"
        private const val ARG_URL = "arg_url"
        private const val ARG_TIEMPO = "arg_tiempo"
        private const val ARG_BLOQUEADO = "arg_bloqueado"

        fun nuevaInstancia(nombre: String, url: String, tiempo: Int, esBloqueado: Boolean): MonitoreoSitioFragment {
            val fragmento = MonitoreoSitioFragment()
            val args = Bundle()
            args.putString(ARG_NOMBRE, nombre)
            args.putString(ARG_URL, url)
            args.putInt(ARG_TIEMPO, tiempo)
            args.putBoolean(ARG_BLOQUEADO, esBloqueado)
            fragmento.arguments = args
            return fragmento
        }
    }

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, s: Bundle?): View {
        _enlace = FragmentMonitoreoSitioBinding.inflate(inflador, contenedor, false)
        return enlace.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nombreSitio = arguments?.getString(ARG_NOMBRE) ?: "Sitio"
        urlSitio = arguments?.getString(ARG_URL) ?: ""
        tiempoSitio = arguments?.getInt(ARG_TIEMPO) ?: 0
        esBloqueado = arguments?.getBoolean(ARG_BLOQUEADO) ?: false

        configurarUI()
    }

    private fun configurarUI() {
        enlace.tvNombreSitio.text = nombreSitio
        enlace.tvUrlSitio.text = urlSitio

        val horas = tiempoSitio / 60
        val minutos = tiempoSitio % 60
        enlace.tvTiempoSitio.text = "Uso hoy: ${horas}h ${minutos}m"

        enlace.imgIconoSitio.setImageResource(R.drawable.img_sitio)

        enlace.btnVolver.setOnClickListener { parentFragmentManager.popBackStack() }

        enlace.switchBloqueoGeneral.isChecked = esBloqueado
        actualizarTextoYColores(esBloqueado)

        enlace.switchBloqueoGeneral.setOnClickListener {
            val quiereBloquear = enlace.switchBloqueoGeneral.isChecked
            ejecutarAccionBloqueo(quiereBloquear)
        }

        enlace.btnAgregarFiltro.setOnClickListener {
            Toast.makeText(context, "Crear regla horaria (Próximamente)", Toast.LENGTH_SHORT).show()
        }

        enlace.rvFiltrosSitio.layoutManager = LinearLayoutManager(context)
        adaptador = FiltroSitioAdapter(listaFiltros, {}, { _, _ -> })
        enlace.rvFiltrosSitio.adapter = adaptador

        actualizarVisibilidadFiltros()
    }

    private fun ejecutarAccionBloqueo(bloquear: Boolean) {
        esBloqueado = bloquear
        actualizarTextoYColores(bloquear)

        viewModel.alternarBloqueoDeSitio(nombreSitio, urlSitio, bloquear)

        val mensaje = if (bloquear) "$nombreSitio ha sido BLOQUEADO" else "$nombreSitio desbloqueado"

        Snackbar.make(enlace.root, mensaje, Snackbar.LENGTH_LONG)
            .setAction("DESHACER") {
                enlace.switchBloqueoGeneral.isChecked = !bloquear
                ejecutarAccionBloqueo(!bloquear)
            }
            .show()
    }

    private fun actualizarTextoYColores(estaBloqueado: Boolean) {
        if (estaBloqueado) {
            val colorRojo = ContextCompat.getColor(requireContext(), R.color.danger)
            enlace.tvNombreSitio.setTextColor(colorRojo)
        } else {
            val colorTitulo = ContextCompat.getColor(requireContext(), R.color.titulo)
            enlace.tvNombreSitio.setTextColor(colorTitulo)
        }
    }

    private fun actualizarVisibilidadFiltros() {
        if (listaFiltros.isEmpty()) {
            enlace.tvSinFiltros.visibility = View.VISIBLE
            enlace.rvFiltrosSitio.visibility = View.GONE
        } else {
            enlace.tvSinFiltros.visibility = View.GONE
            enlace.rvFiltrosSitio.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _enlace = null
    }
}