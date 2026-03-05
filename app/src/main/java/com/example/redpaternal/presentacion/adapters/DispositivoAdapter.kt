package com.example.redpaternal.presentacion.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.redpaternal.R
import com.example.redpaternal.datos.modelo.Dispositivo
import com.example.redpaternal.datos.modelo.TipoDispositivo

class DispositivoAdapter(
    private val listaDispositivos: List<Dispositivo>,
    private val alHacerClic: (Dispositivo) -> Unit
) : RecyclerView.Adapter<DispositivoAdapter.VisorDispositivo>() {

    override fun onCreateViewHolder(padre: ViewGroup, tipoVista: Int): VisorDispositivo {
        val vista = LayoutInflater.from(padre.context)
            .inflate(R.layout.item_dispositivo_card, padre, false)
        return VisorDispositivo(vista)
    }

    override fun onBindViewHolder(visor: VisorDispositivo, posicion: Int) {
        visor.vincular(listaDispositivos[posicion])
    }

    override fun getItemCount() = listaDispositivos.size

    inner class VisorDispositivo(vistaItem: View) : RecyclerView.ViewHolder(vistaItem) {

        private val tvNombre: TextView = vistaItem.findViewById(R.id.tvNombreDispositivo)
        private val tvMac: TextView = vistaItem.findViewById(R.id.tvMacDispositivo)
        private val tvInfo: TextView = vistaItem.findViewById(R.id.tvInfoConexion)
        private val vistaEstado: View = vistaItem.findViewById(R.id.vistaEstadoConexion)
        private val imgIcono: ImageView = vistaItem.findViewById(R.id.imgIconoDispositivo)
        private val imgNotif: ImageView = vistaItem.findViewById(R.id.imgNotificacionActiva)
        private val contexto: Context = vistaItem.context

        fun vincular(dispositivo: Dispositivo) {
            tvNombre.text = dispositivo.nombre
            tvMac.text = dispositivo.macAddress

            val iconoRes = when (dispositivo.tipo) {
                TipoDispositivo.COMPUTADORA -> R.drawable.img_pc
                TipoDispositivo.TV -> R.drawable.img_pc
                TipoDispositivo.CONSOLA -> R.drawable.img_pc
                TipoDispositivo.TELEFONO, TipoDispositivo.IOT -> R.drawable.img_telefono
                else -> R.drawable.img_telefono
            }
            imgIcono.setImageResource(iconoRes)

            val tiempoFormateado = formatearTiempo(dispositivo.tiempoConectadoHoy)

            if (dispositivo.estaConectado) {
                configurarEstadoConectado(tiempoFormateado)
            } else {
                configurarEstadoDesconectado(tiempoFormateado)
            }

            val prefs = contexto.getSharedPreferences("monitoreo_prefs", Context.MODE_PRIVATE)
            val notifActiva = prefs.getBoolean("activo_${dispositivo.macAddress}", false)
            imgNotif.visibility = if (notifActiva) View.VISIBLE else View.GONE

            itemView.setOnClickListener { alHacerClic(dispositivo) }
        }

        private fun configurarEstadoConectado(tiempoTexto: String) {
            vistaEstado.background?.setTint(ContextCompat.getColor(contexto, R.color.success))
            vistaEstado.alpha = 1.0f
            imgIcono.alpha = 1.0f
            tvNombre.alpha = 1.0f
            tvInfo.setTextColor(ContextCompat.getColor(contexto, R.color.success))
            tvInfo.text = "En línea • $tiempoTexto hoy"
        }

        private fun configurarEstadoDesconectado(tiempoTexto: String) {
            vistaEstado.background?.setTint(ContextCompat.getColor(contexto, R.color.texto))
            vistaEstado.alpha = 0.5f
            imgIcono.alpha = 0.5f
            tvNombre.alpha = 0.7f
            tvInfo.setTextColor(ContextCompat.getColor(contexto, R.color.texto))
            tvInfo.text = "Offline • $tiempoTexto hoy"
        }

        private fun formatearTiempo(segundosStr: String): String {
            val totalSegundos = segundosStr.toLongOrNull() ?: 0L
            if (totalSegundos < 60) return "${totalSegundos}s"
            val horas = totalSegundos / 3600
            val minutos = (totalSegundos % 3600) / 60
            return if (horas > 0) "${horas}h ${minutos}m" else "${minutos}m"
        }
    }
}