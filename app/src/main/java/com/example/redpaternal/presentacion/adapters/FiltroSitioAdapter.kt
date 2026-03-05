package com.example.redpaternal.presentacion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.redpaternal.R
import com.example.redpaternal.datos.modelo.FiltroSitio
import com.google.android.material.materialswitch.MaterialSwitch

class FiltroSitioAdapter(
    private val listaFiltros: List<FiltroSitio>,
    private val alClickeaFiltro: (FiltroSitio) -> Unit,
    private val alCambiarSwitch: (FiltroSitio, Boolean) -> Unit
) : RecyclerView.Adapter<FiltroSitioAdapter.VisorFiltro>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VisorFiltro {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.item_filtro_sitio, parent, false)
        return VisorFiltro(vista)
    }

    override fun onBindViewHolder(holder: VisorFiltro, position: Int) {
        holder.vincular(listaFiltros[position])
    }

    override fun getItemCount() = listaFiltros.size

    inner class VisorFiltro(vista: View) : RecyclerView.ViewHolder(vista) {
        val tvNombre: TextView = vista.findViewById(R.id.tvNombreFiltro)
        val tvDetalle: TextView = vista.findViewById(R.id.tvDetalleFiltro)
        val switch: MaterialSwitch = vista.findViewById(R.id.switchFiltro)
        val contenedor: LinearLayout = vista.findViewById(R.id.contenedorItemFiltro)

        fun vincular(filtro: FiltroSitio) {
            tvNombre.text = filtro.nombre

            // Formatear días
            val diasTexto = if (filtro.diasSemana.size == 7) "Todos los días" else "Días específicos"
            tvDetalle.text = "$diasTexto • ${filtro.horaInicio} - ${filtro.horaFin}"

            // Listener del Switch (Importante: quitar antes de setear para evitar bucles)
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = filtro.estaActivo

            switch.setOnCheckedChangeListener { _, isChecked ->
                alCambiarSwitch(filtro, isChecked)
            }

            contenedor.setOnClickListener { alClickeaFiltro(filtro) }
        }
    }
}