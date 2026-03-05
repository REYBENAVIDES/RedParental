package com.example.redpaternal.presentacion.dashboard.routerdetalle.filtros

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.redpaternal.R

class SitioSeleccionadoAdapter(
    private val listaSitios: MutableList<String>,
    private val alEliminar: (String) -> Unit
) : RecyclerView.Adapter<SitioSeleccionadoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreSitio) // Asegúrate de tener este ID en item_sitio_seleccionado.xml
        val btnBorrar: ImageView = view.findViewById(R.id.btnEliminarSitio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Usaremos un layout simple provisional o el que tengas definido
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sitio_seleccionado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sitio = listaSitios[position]
        holder.tvNombre.text = sitio
        holder.btnBorrar.setOnClickListener {
            alEliminar(sitio)
        }
    }

    override fun getItemCount() = listaSitios.size
}