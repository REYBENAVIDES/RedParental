package com.example.redpaternal.presentacion.router_configurar.fragmentos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.redpaternal.databinding.FragmentInstruccionesBinding
import com.example.redpaternal.presentacion.router_configurar.RouterConfigurarActivity

class InstruccionesFragment : Fragment() {
    private var _binding: FragmentInstruccionesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInstruccionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNextInstrucciones.setOnClickListener {
            (activity as? RouterConfigurarActivity)?.mostrarFragmentoEscaneo()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}