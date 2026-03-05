package com.example.redpaternal.presentacion.onboarding.bienvenida.fragmentos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.redpaternal.databinding.FragmentBeneficiosBinding
import com.example.redpaternal.presentacion.onboarding.bienvenida.ActividadOnboarding

class BeneficiosFragment : Fragment() {
    private var _binding: FragmentBeneficiosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBeneficiosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.btnNextBenefits.setOnClickListener {
            (activity as? ActividadOnboarding)?.irAPagina(2)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}