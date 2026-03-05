package com.example.redpaternal.presentacion.onboarding.bienvenida.fragmentos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.redpaternal.databinding.FragmentSeguridadBinding
import com.example.redpaternal.presentacion.onboarding.bienvenida.ActividadOnboarding

class SeguridadFragment : Fragment() {
    private var _binding: FragmentSeguridadBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSeguridadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNextSecurity.setOnClickListener {
            (activity as? ActividadOnboarding)?.irAPagina(3)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}