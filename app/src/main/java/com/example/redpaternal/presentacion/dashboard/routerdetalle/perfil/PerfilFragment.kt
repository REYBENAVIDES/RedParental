package com.example.redpaternal.presentacion.dashboard.routerdetalle.perfil

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentPerfilRouterBinding
import com.example.redpaternal.datos.modelo.RouterInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PerfilFragment : Fragment() {

    private var _enlace: FragmentPerfilRouterBinding? = null
    private val enlace get() = _enlace!!

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        _enlace = FragmentPerfilRouterBinding.inflate(inflador, contenedor, false)
        return enlace.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val routerInfo = cargarDatosRouter()

        if (routerInfo != null) {
            pintarDatos(routerInfo)
            configurarBotones(routerInfo)
        } else {
            Toast.makeText(context, "Error cargando datos del router", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarDatosRouter(): RouterInfo? {
        return requireActivity().intent.getSerializableExtra("router_info") as? RouterInfo
    }

    private fun pintarDatos(info: RouterInfo) {
        enlace.tvNombreRed.text = info.nombreRed
        enlace.tvMarcaModelo.text = "${info.marca} ${info.modelo}"
        enlace.tvMacRouter.text = info.macRouter.uppercase()

        enlace.tvIpPuertaEnlace.text = info.ipPuertaEnlace
        enlace.tvDireccionMac.text = info.macRouter.uppercase()
        enlace.tvNextDnsProfile.text = info.nextDnsProfileId ?: "No configurado"

        enlace.tvFechaConfiguracion.text = formatearFecha(info.fechaConfiguracion)
        enlace.tvNumeroSerie.text = if (!info.numeroSerie.isNullOrEmpty()) info.numeroSerie else "No disponible"
    }

    private fun configurarBotones(info: RouterInfo) {
        enlace.btnVolver.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        enlace.btnCopiarIp.setOnClickListener {
            copiarAlPortapapeles("IP Puerta de Enlace", info.ipPuertaEnlace)
        }

        enlace.btnCopiarMac.setOnClickListener {
            copiarAlPortapapeles("Dirección MAC", info.macRouter)
        }

        enlace.btnCopiarNextDns.setOnClickListener {
            info.nextDnsProfileId?.let { id ->
                copiarAlPortapapeles("NextDNS ID", id)
            }
        }

        enlace.btnSalirRouter.setOnClickListener {
            salirDelRouter()
        }
    }

    private fun copiarAlPortapapeles(etiqueta: String, texto: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(etiqueta, texto)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$etiqueta copiado", Toast.LENGTH_SHORT).show()
    }

    private fun salirDelRouter() {
        requireActivity().finish()
    }

    private fun formatearFecha(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return "Desconocida"
        return try {
            val sdf = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Fecha inválida"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _enlace = null
    }
}