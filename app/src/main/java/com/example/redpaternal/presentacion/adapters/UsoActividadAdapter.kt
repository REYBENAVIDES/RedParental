package com.example.redpaternal.presentacion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.redpaternal.R
import com.example.redpaternal.datos.modelo.SitioVisitado
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Date

class UsoActividadAdapter(
    private var listaSitios: List<SitioVisitado>,
    private var tiempoTotalMinutosDia: Int, // Referencia para la barra (0-100)
    private val onItemClick: (SitioVisitado) -> Unit
) : RecyclerView.Adapter<UsoActividadAdapter.VisorUso>() {

    // --- FUNCIÓN CLAVE PARA EVITAR PARPADEO Y RECREACIÓN ---
    fun actualizarDatos(nuevosSitios: List<SitioVisitado>, nuevoTotalMinutos: Int) {
        this.listaSitios = nuevosSitios
        this.tiempoTotalMinutosDia = nuevoTotalMinutos
        // Lo ideal sería usar DiffUtil, pero notifyDataSetChanged() es suficiente aquí
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(padre: ViewGroup, viewType: Int): VisorUso {
        val vista = LayoutInflater.from(padre.context)
            .inflate(R.layout.item_uso_actividad, padre, false)
        return VisorUso(vista)
    }

    override fun onBindViewHolder(holder: VisorUso, position: Int) {
        holder.vincular(listaSitios[position])
    }

    override fun getItemCount() = listaSitios.size

    inner class VisorUso(vistaItem: View) : RecyclerView.ViewHolder(vistaItem) {
        private val tvNombre: TextView = vistaItem.findViewById(R.id.tvNombreApp)
        private val tvCategoria: TextView = vistaItem.findViewById(R.id.tvCategoriaApp)
        private val tvTiempo: TextView = vistaItem.findViewById(R.id.tvTiempoUso)
        private val barraProgreso: LinearProgressIndicator = vistaItem.findViewById(R.id.progressUso)
        private val badgeEnUso: TextView = vistaItem.findViewById(R.id.tvEnUsoBadge)
        private val imgIcono: ImageView = vistaItem.findViewById(R.id.imgIconoApp)

        fun vincular(sitio: SitioVisitado) {
            tvNombre.text = sitio.nombre
            tvCategoria.text = if (sitio.categoria.isNotEmpty()) sitio.categoria else "General"

            imgIcono.setImageResource(R.drawable.img_sitio)

            // --- 1. TIEMPO (Segundos -> H:M) ---
            val segundos = sitio.tiempoHoySegundos
            val horas = segundos / 3600
            val minutos = (segundos % 3600) / 60

            if (horas > 0) {
                tvTiempo.text = "${horas}h ${minutos}m"
            } else {
                tvTiempo.text = "${minutos}m"
            }

            // --- 2. BARRA DE PROGRESO ---
            val minutosTotalesSitio = (segundos / 60).toFloat()
            val porcentaje = if (tiempoTotalMinutosDia > 0) {
                (minutosTotalesSitio / tiempoTotalMinutosDia.toFloat()) * 100
            } else 0f
            barraProgreso.progress = porcentaje.toInt()

            // --- 3. BADGE EN USO (Protección NullPointerException) ---
            val fechaMilis = sitio.ultimaActualizacion?.toDate()?.time
            val esReciente = if (fechaMilis != null) {
                val diferencia = Date().time - fechaMilis
                diferencia < (5 * 60 * 1000) // 5 minutos
            } else false

            badgeEnUso.visibility = if (esReciente) View.VISIBLE else View.GONE

            // --- 4. BLOQUEO ---
            if (sitio.esBloqueado) {
                tvNombre.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                tvCategoria.text = "Bloqueado"
                tvCategoria.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
            } else {
                tvNombre.setTextColor(itemView.context.getColor(R.color.titulo))
                tvCategoria.setTextColor(itemView.context.getColor(R.color.texto))
            }

            itemView.setOnClickListener { onItemClick(sitio) }
        }
    }
}