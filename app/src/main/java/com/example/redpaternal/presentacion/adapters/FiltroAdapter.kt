package com.example.redpaternal.presentacion.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.redpaternal.R
import com.example.redpaternal.databinding.ItemFiltroBinding // Asegúrate de tener ViewBinding activo
import com.example.redpaternal.datos.modelo.FiltroSitio

class FiltroAdapter(
    private var listaFiltros: List<FiltroSitio>,
    private val alClickeaEditar: (FiltroSitio) -> Unit,
    private val alClickeaEliminar: (FiltroSitio) -> Unit,
    private val alCambiarEstado: (FiltroSitio, Boolean) -> Unit
) : RecyclerView.Adapter<FiltroAdapter.FiltroViewHolder>() {

    fun actualizarLista(nuevaLista: List<FiltroSitio>) {
        listaFiltros = nuevaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FiltroViewHolder {
        // Inflamos el layout item_filtro.xml
        val binding = ItemFiltroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FiltroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FiltroViewHolder, position: Int) {
        holder.bind(listaFiltros[position])
    }

    override fun getItemCount(): Int = listaFiltros.size

    inner class FiltroViewHolder(private val binding: ItemFiltroBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(filtro: FiltroSitio) {
            val context = binding.root.context

            // 1. Nombre
            binding.tvNombreFiltro.text = filtro.nombre

            // 2. Icono Principal (Opcional: cambiar según alcance)
            if (filtro.tipoAlcance == "ROUTER") {
                binding.imgIconoFiltro.setImageResource(R.drawable.img_router) // Usa tu icono de router
                binding.tvAlcanceFiltro.text = "Toda la red (Todos los dispositivos)"
            } else {
                binding.imgIconoFiltro.setImageResource(R.drawable.img_telefono) // Usa tu icono de telefono/dispositivo
                val count = filtro.listaDispositivos.size
                binding.tvAlcanceFiltro.text = if (count == 1) "1 dispositivo específico" else "$count dispositivos específicos"
            }

            // 3. Horario (Lógica de texto)
            if (filtro.esSiempreActivo) {
                binding.tvHorarioFiltro.text = "Siempre activo (24/7)"
            } else {
                val diasTexto = formatearDias(filtro.diasSemana)
                binding.tvHorarioFiltro.text = "$diasTexto • ${filtro.horaInicio} - ${filtro.horaFin}"
            }

            // 4. Cantidad de Sitios (Badge Amarillo claro)
            val numSitios = filtro.listaSitios.size
            binding.tvCantidadSitios.text = if (numSitios == 1) "1 sitio" else "$numSitios sitios"

            // 5. Tipo de Acción (Badge Bloqueado/Permitido)
            if (filtro.tipoAccion == "BLOQUEAR") {
                binding.tvTipoAccion.text = "Bloqueado"
                // Texto Rojo
                binding.tvTipoAccion.setTextColor(ContextCompat.getColor(context, R.color.danger))
                // Fondo Rojo Claro (CardView)
                binding.chipTipoAccion.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
            } else {
                binding.tvTipoAccion.text = "Permitido"
                // Texto Verde
                binding.tvTipoAccion.setTextColor(ContextCompat.getColor(context, R.color.success))
                // Fondo Verde Claro
                binding.chipTipoAccion.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
            }

            // 6. Switch de Activación (Manejo seguro para listas)
            binding.switchFiltroActivo.setOnCheckedChangeListener(null) // Importante: Remover listener previo
            binding.switchFiltroActivo.isChecked = filtro.estaActivo
            binding.switchFiltroActivo.setOnCheckedChangeListener { _, isChecked ->
                alCambiarEstado(filtro, isChecked)
            }

            // 7. Menú de Opciones (3 puntos)
            binding.btnEliminar.setOnClickListener { view ->
                alClickeaEliminar(filtro)
            }

            // Clic en toda la tarjeta para editar
            binding.root.setOnClickListener { alClickeaEditar(filtro) }
        }

        // Función auxiliar para convertir [2,3,4] en "Lun, Mar, Mié"
        private fun formatearDias(dias: List<Int>): String {
            if (dias.isEmpty()) return "Ningún día"
            if (dias.size == 7) return "Todos los días"

            // Asumiendo formato Calendar: 1=Dom, 2=Lun, ... 7=Sab
            val nombresCortos = listOf("", "Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

            // Ordenamos y mapeamos
            return dias.sorted().joinToString(", ") { dia ->
                if (dia in 1..7) nombresCortos[dia] else ""
            }
        }
    }
}