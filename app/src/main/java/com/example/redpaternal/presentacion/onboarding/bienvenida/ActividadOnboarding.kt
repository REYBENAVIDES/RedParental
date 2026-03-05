package com.example.redpaternal.presentacion.onboarding.bienvenida

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.redpaternal.databinding.ActivityOnboardingBinding
import com.example.redpaternal.datos.remoto.AyudanteBaseDatosFirebase
import com.example.redpaternal.presentacion.dashboard.home.HomeActivity
import com.example.redpaternal.presentacion.router_configurar.RouterConfigurarActivity
import com.example.redpaternal.utilidades.PreferenciasManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class ActividadOnboarding : AppCompatActivity() {
    private lateinit var enlace: ActivityOnboardingBinding
    private lateinit var clienteGoogleSignIn: GoogleSignInClient
    private lateinit var gestorPreferencias: PreferenciasManager
    private lateinit var repositorio: AyudanteBaseDatosFirebase

    private val lanzadorGoogleSignIn = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            manejarResultadoGoogleSignIn(resultado.data)
        } else {
            Snackbar.make(enlace.root, "Inicio de sesión cancelado", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enlace = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(enlace.root)
        supportActionBar?.hide()

        repositorio = AyudanteBaseDatosFirebase(this)
        gestorPreferencias = PreferenciasManager(this)

        val opcionesGoogle = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("159591751405-m5vd7dcdkqff5cltdtm3ikc1bejqj4gg.apps.googleusercontent.com")
            .requestEmail()
            .build()
        clienteGoogleSignIn = GoogleSignIn.getClient(this, opcionesGoogle)

        if (repositorio.obtenerUsuarioActual() != null) {
            procesarUsuarioExistente()
        } else {
            configurarInterfaz()
        }
    }

    fun irAPagina(indice: Int) {
        if (::enlace.isInitialized) {
            enlace.viewPager.setCurrentItem(indice, true)
        }
    }

    fun marcarOnboardingComoTerminado() {
        if (gestorPreferencias.esPrimeraVez()) {
            gestorPreferencias.marcarPrimeraVezCompletada()
        }
    }

    private fun configurarInterfaz() {
        enlace.viewPager.isUserInputEnabled = false
        val esPrimeraVez = gestorPreferencias.esPrimeraVez()
        val adaptador = OnboardingPagerAdapter(this, esPrimeraVez)
        enlace.viewPager.adapter = adaptador

        if (esPrimeraVez) {
            TabLayoutMediator(enlace.tabIndicator, enlace.viewPager) { _, _ -> }.attach()
        } else {
            enlace.viewPager.setCurrentItem(0, false)
        }
    }

    fun iniciarInicioSesionGoogle() {
        lanzadorGoogleSignIn.launch(clienteGoogleSignIn.signInIntent)
    }

    private fun manejarResultadoGoogleSignIn(datos: Intent?) {
        try {
            val tarea = GoogleSignIn.getSignedInAccountFromIntent(datos)
            val cuenta = tarea.getResult(ApiException::class.java)

            if (cuenta != null) {
                lifecycleScope.launch {
                    when (val resultadoAuth = repositorio.autenticarConGoogle(cuenta)) {
                        is AyudanteBaseDatosFirebase.Resultado.Exito -> {
                            procesarLogicaNegocio(resultadoAuth.datos)
                        }
                        is AyudanteBaseDatosFirebase.Resultado.Error -> {
                            Log.e("Onboarding", resultadoAuth.mensaje, resultadoAuth.excepcion)
                            Snackbar.make(enlace.root, "Error de autenticación", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e("Onboarding", "Fallo Google: ${e.statusCode}")
        }
    }

    private fun procesarUsuarioExistente() {
        val usuario = repositorio.obtenerUsuarioActual() ?: return
        lifecycleScope.launch {
            procesarLogicaNegocio(usuario)
        }
    }

    private suspend fun procesarLogicaNegocio(usuario: com.google.firebase.auth.FirebaseUser) {
        when (val resultadoBD = repositorio.verificarOActualizarUsuario(usuario)) {
            is AyudanteBaseDatosFirebase.Resultado.Exito -> {
                val usuarioYaExiste = resultadoBD.datos
                if (usuarioYaExiste) {
                    irAActividadPrincipal()
                } else {
                    irAConfigurarRouter()
                }
            }
            is AyudanteBaseDatosFirebase.Resultado.Error -> {
                Log.e("Onboarding", resultadoBD.mensaje)
                irAActividadPrincipal()
            }
        }
    }

    private fun irAConfigurarRouter() {
        startActivity(Intent(this, RouterConfigurarActivity::class.java))
        finish()
    }

    private fun irAActividadPrincipal() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}