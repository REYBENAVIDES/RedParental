package com.example.redpaternal.presentacion.router_configurar.fragmentos

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.redpaternal.databinding.FragmentConexionExitosaBinding
import com.example.redpaternal.presentacion.dashboard.home.HomeActivity

class ConexionExitosaFragment : Fragment() {

    private var _binding: FragmentConexionExitosaBinding? = null
    private val binding get() = _binding!!
    private val redirectDelay: Long = 5000
    private var redirectHandler = Handler(Looper.getMainLooper())
    private var redirectRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConexionExitosaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.setOnClickListener {
            navigateToMainActivity()
        }

        startAutoRedirect()
    }

    private fun startAutoRedirect() {
        redirectRunnable = Runnable {
            navigateToMainActivity()
        }

        redirectHandler.postDelayed(redirectRunnable!!, redirectDelay)
    }

    private fun navigateToMainActivity() {
        redirectRunnable?.let { redirectHandler.removeCallbacks(it) }

        val intent = Intent(requireActivity(), HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        requireActivity().finish()

        requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        redirectRunnable?.let { redirectHandler.removeCallbacks(it) }
        _binding = null
    }
}