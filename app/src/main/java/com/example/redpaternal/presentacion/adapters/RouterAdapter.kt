package com.example.redpaternal.presentacion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.redpaternal.R
import com.example.redpaternal.datos.modelo.RouterInfo

// Aceptamos una función lambda para manejar el clic desde el Fragmento
class RouterAdapter(
    private val listaRouters: List<RouterInfo>,
    private val alHacerClic: (RouterInfo) -> Unit
) : RecyclerView.Adapter<RouterAdapter.VisorRouter>() {

    // Variable para saber cuál es la MAC conectada actualmente
    var macActualConectada: String? = null

    override fun onCreateViewHolder(padre: ViewGroup, tipoVista: Int): VisorRouter {
        val vista = LayoutInflater.from(padre.context)
            .inflate(R.layout.item_router_card, padre, false)
        return VisorRouter(vista)
    }

    override fun onBindViewHolder(visor: VisorRouter, posicion: Int) {
        visor.vincular(listaRouters[posicion], macActualConectada, alHacerClic)
    }

    override fun getItemCount() = listaRouters.size

    class VisorRouter(vistaItem: View) : RecyclerView.ViewHolder(vistaItem) {
        private val tvModelo: TextView = vistaItem.findViewById(R.id.tvModeloRouter)
        private val tvRed: TextView = vistaItem.findViewById(R.id.tvRedRouter)
        private val tvIp: TextView = vistaItem.findViewById(R.id.tvIpRouter)
        private val contenedorEstado: LinearLayout = vistaItem.findViewById(R.id.contenedorEstado)
        private val tvEstado: TextView = vistaItem.findViewById(R.id.tvEstadoTexto)

        fun vincular(router: RouterInfo, macActual: String?, clickListener: (RouterInfo) -> Unit) {
            tvModelo.text = router.modelo
            tvRed.text = router.nombreRed
            tvIp.text = router.ipPuertaEnlace

            // --- LÓGICA VISUAL DE ESTADO ---
            // Si la MAC del router guardado coincide con la del WiFi actual del celular
            val esElRouterActual = !macActual.isNullOrEmpty() &&
                    router.macRouter.equals(macActual, ignoreCase = true)

            if (esElRouterActual) {
                contenedorEstado.visibility = View.VISIBLE
                tvEstado.text = "Conectado"
            } else {
                contenedorEstado.visibility = View.GONE
            }

            // Al hacer clic, invocamos la función que nos pasó el Fragmento
            itemView.setOnClickListener {
                clickListener(router)
            }
        }
    }
}