package com.example.redpaternal.presentacion.dashboard.home.fragmentos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.redpaternal.R
import com.example.redpaternal.databinding.FragmentPerfilUsuarioBinding
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.presentacion.onboarding.bienvenida.ActividadOnboarding
import kotlinx.coroutines.launch

class PerfilUsuarioFragment : Fragment() {

    private var _enlace: FragmentPerfilUsuarioBinding? = null
    private val enlace get() = _enlace!!

    private lateinit var ayudanteBD: AyudanteBaseDatosFirebase

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        _enlace = FragmentPerfilUsuarioBinding.inflate(inflador, contenedor, false)
        ayudanteBD = AyudanteBaseDatosFirebase(requireContext())
        return enlace.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        cargarDatos()
        configurarBotones()
    }

    private fun cargarDatos() {
        val usuario = ayudanteBD.obtenerUsuarioActual() ?: return

        enlace.tvCorreoUsuario.text = usuario.email

        if (usuario.photoUrl != null) {
            Glide.with(this)
                .load(usuario.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.img_logo)
                .into(enlace.imgPerfilGrande)
        }

        lifecycleScope.launch {
            val nombreBD = ayudanteBD.obtenerNombreGuardado()
            enlace.tvNombreUsuario.text = nombreBD ?: usuario.displayName ?: "Usuario"
        }
    }

    private fun configurarBotones() {
        enlace.btnVolver.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        enlace.btnOpcionModoOscuro.setOnClickListener {
            enlace.switchModoOscuro.isChecked = !enlace.switchModoOscuro.isChecked

            if (enlace.switchModoOscuro.isChecked) {
                Toast.makeText(context, "Modo Oscuro: ACTIVADO", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Modo Oscuro: DESACTIVADO", Toast.LENGTH_SHORT).show()
            }
        }

        enlace.btnOpcionAyuda.setOnClickListener {
            Toast.makeText(context, "Abriendo centro de ayuda...", Toast.LENGTH_SHORT).show()
        }

        enlace.btnOpcionTerminos.setOnClickListener {
            Toast.makeText(context, "Mostrando términos legales...", Toast.LENGTH_SHORT).show()
        }

        enlace.btnOpcionCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun cerrarSesion() {
        ayudanteBD.cerrarSesion()

        val intento = Intent(requireContext(), ActividadOnboarding::class.java)
        intento.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intento)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _enlace = null
    }
}