package com.example.redpaternal.presentacion.onboarding.bienvenida

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.redpaternal.presentacion.onboarding.bienvenida.fragmentos.BeneficiosFragment
import com.example.redpaternal.presentacion.onboarding.bienvenida.fragmentos.BienvenidaFragment
import com.example.redpaternal.presentacion.onboarding.bienvenida.fragmentos.LoginFragment
import com.example.redpaternal.presentacion.onboarding.bienvenida.fragmentos.SeguridadFragment

class OnboardingPagerAdapter(
    actividadFragmento: FragmentActivity,
    private val mostrarBienvenidaCompleta: Boolean
) : FragmentStateAdapter(actividadFragmento) {

    override fun getItemCount(): Int {
        return if (mostrarBienvenidaCompleta) {
            4
        } else {
            1
        }
    }

    override fun createFragment(posicion: Int): Fragment {
        return if (mostrarBienvenidaCompleta) {
            when (posicion) {
                0 -> BienvenidaFragment()
                1 -> BeneficiosFragment()
                2 -> SeguridadFragment()
                3 -> LoginFragment()
                else -> LoginFragment()
            }
        } else {
            LoginFragment()
        }
    }
}