package com.example.redpaternal.presentacion.router_configurar.fragmentos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentRouterScanBinding
import com.example.redpaternal.databinding.ItemRouterDeviceBinding
import com.example.redpaternal.datos.modelo.RouterInfo
import com.example.redpaternal.datos.remoto.AyudanteRed
import com.example.redpaternal.presentacion.router_configurar.RouterConfigurarActivity
import kotlinx.coroutines.launch

class RouterScanFragment : Fragment() {
    private var _binding: FragmentRouterScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var routersAdapter: RoutersAdapter
    // Lista de routers encontrados (en WiFi normal siempre es 1, el conectado)
    private var detectedRouters = mutableListOf<RouterInfo>()
    private var selectedRouter: RouterInfo? = null

    // Permiso de ubicación necesario para leer SSID/BSSID en Android 10+
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                ejecutarEscaneoReal()
            } else {
                Toast.makeText(requireContext(), "Se requiere permiso de ubicación para leer el nombre del WiFi", Toast.LENGTH_LONG).show()
                showNoDevices()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRouterScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupListeners()
        iniciarProcesoEscaneo()
    }

    private fun setupViewPager() {
        routersAdapter = RoutersAdapter()
        binding.viewPagerRouters.adapter = routersAdapter

        binding.viewPagerRouters.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedRouter = detectedRouters.getOrNull(position)
                updateDotsIndicator(position)
            }
        })
    }

    private fun setupListeners() {
        binding.btnRescan.setOnClickListener {
            iniciarProcesoEscaneo()
        }

        binding.btnAction.setOnClickListener {
            if (detectedRouters.isEmpty()) {
                iniciarProcesoEscaneo()
            } else {
                // Pasamos el router REAL encontrado a la siguiente pantalla
                selectedRouter?.let { router ->
                    (activity as? RouterConfigurarActivity)?.mostrarFragmentoCredenciales(router)
                }
            }
        }
    }

    private fun iniciarProcesoEscaneo() {
        showScanningState()

        // Verificar permisos antes de escanear
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ejecutarEscaneoReal()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun ejecutarEscaneoReal() {
        lifecycleScope.launch {
            // 1. Obtener datos locales (rápido)
            val routerEncontrado = AyudanteRed.obtenerRouterActual(requireContext())

            detectedRouters.clear()
            selectedRouter = null

            if (routerEncontrado != null) {
                // 2. Consultar API Fabricante (Red) para ver si es TP-Link
                try {
                    val fabricante = AyudanteRed.obtenerFabricante(routerEncontrado.macRouter)
                    routerEncontrado.marca = fabricante

                    // Ajuste visual para el modelo
                    if (fabricante.contains("TP-Link", ignoreCase = true)) {
                        routerEncontrado.modelo = "TP-Link Device (Detectado)"
                    } else {
                        routerEncontrado.modelo = "$fabricante Generic"
                    }
                } catch (e: Exception) {
                    routerEncontrado.modelo = "Router WiFi"
                }

                // Agregamos a la lista
                detectedRouters.add(routerEncontrado)
                showDevicesFound(detectedRouters)
            } else {
                showNoDevices()
                // Sugerencia visual
                Toast.makeText(context, "No estás conectado a WiFi o falta GPS", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- MÉTODOS VISUALES (Sin cambios lógicos grandes, solo manejo de UI) ---

    private fun showScanningState() {
        binding.containerScanning.visibility = View.VISIBLE
        binding.viewPagerRouters.visibility = View.GONE
        binding.dotsIndicator.visibility = View.GONE
        binding.containerNoDevices.visibility = View.GONE
        binding.btnAction.visibility = View.GONE
        binding.cardScanInfo.visibility = View.GONE
        binding.lottieScanAnimation.playAnimation()
    }

    private fun showDevicesFound(routers: List<RouterInfo>) {
        binding.containerScanning.visibility = View.GONE
        binding.viewPagerRouters.visibility = View.VISIBLE
        binding.dotsIndicator.visibility = View.VISIBLE
        binding.containerNoDevices.visibility = View.GONE
        binding.btnAction.visibility = View.VISIBLE
        binding.cardScanInfo.visibility = View.GONE

        routersAdapter.submitList(routers)
        setupDotsIndicator(routers.size)
        selectedRouter = routers.firstOrNull()
        updateDotsIndicator(0)
        binding.btnAction.text = "Conectar Router"
    }

    private fun showNoDevices() {
        binding.containerScanning.visibility = View.GONE
        binding.viewPagerRouters.visibility = View.GONE
        binding.dotsIndicator.visibility = View.GONE
        binding.containerNoDevices.visibility = View.VISIBLE
        binding.btnAction.visibility = View.VISIBLE
        binding.cardScanInfo.visibility = View.VISIBLE
        binding.btnAction.text = "Volver a Escanear"
    }

    // ========== INDICADOR DE PUNTOS ==========
    private fun setupDotsIndicator(count: Int) {
        binding.dotsIndicator.removeAllViews()
        for (i in 0 until count) {
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 0) 16.dp else 8.dp, 8.dp
                ).apply { marginEnd = if (i < count - 1) 8.dp else 0 }
                background = createDotDrawable(i == 0)
            }
            binding.dotsIndicator.addView(dot)
        }
    }

    private fun updateDotsIndicator(selectedPosition: Int) {
        for (i in 0 until binding.dotsIndicator.childCount) {
            val dot = binding.dotsIndicator.getChildAt(i)
            dot.layoutParams = LinearLayout.LayoutParams(
                if (i == selectedPosition) 16.dp else 8.dp, 8.dp
            ).apply { marginEnd = if (i < binding.dotsIndicator.childCount - 1) 8.dp else 0 }
            dot.background = createDotDrawable(i == selectedPosition)
        }
    }

    private fun createDotDrawable(isSelected: Boolean): Drawable {
        val color = if (isSelected) ContextCompat.getColor(requireContext(), R.color.primario)
        else ContextCompat.getColor(requireContext(), R.color.secondario)
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(color)
        shape.cornerRadius = 4.dp.toFloat()
        return shape
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    // ========== ADAPTER INTERNO ==========
    private inner class RoutersAdapter : RecyclerView.Adapter<RoutersAdapter.RouterViewHolder>() {
        private val routers = mutableListOf<RouterInfo>()

        fun submitList(newRouters: List<RouterInfo>) {
            routers.clear()
            routers.addAll(newRouters)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouterViewHolder {
            val binding = ItemRouterDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RouterViewHolder(binding)
        }

        override fun onBindViewHolder(holder: RouterViewHolder, position: Int) {
            holder.bind(routers[position])
        }

        override fun getItemCount(): Int = routers.size

        inner class RouterViewHolder(private val binding: ItemRouterDeviceBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(router: RouterInfo) {
                binding.tvRouterName.text = router.nombreRed
                binding.tvRouterMac.text = "MAC: ${router.macRouter}" // Usamos BSSID del modelo actualizado
                binding.tvRouterModel.text = "${router.marca} (${router.ipPuertaEnlace})"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}