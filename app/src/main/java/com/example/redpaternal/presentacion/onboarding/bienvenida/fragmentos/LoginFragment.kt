package com.example.redpaternal.presentacion.onboarding.bienvenida.fragmentos

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.redpaternal.databinding.FragmentLoginBinding
import com.example.redpaternal.presentacion.onboarding.bienvenida.ActividadOnboarding

class LoginFragment : Fragment() {
    private var _enlace: FragmentLoginBinding? = null
    private val enlace get() = _enlace!!

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estadoGuardado: Bundle?): View {
        _enlace = FragmentLoginBinding.inflate(inflador, contenedor, false)
        return enlace.root
    }

    override fun onViewCreated(vista: View, estadoGuardado: Bundle?) {
        super.onViewCreated(vista, estadoGuardado)

        enlace.btnGoogleLogin.setOnClickListener {
            Log.d("LoginFragment", "Iniciando proceso de login...")
            (requireActivity() as? ActividadOnboarding)?.iniciarInicioSesionGoogle()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("LoginFragment", "Login visible -> Finalizando onboarding")
        (activity as? ActividadOnboarding)?.marcarOnboardingComoTerminado()
    }

    override fun onDestroyView() { super.onDestroyView(); _enlace = null }
}