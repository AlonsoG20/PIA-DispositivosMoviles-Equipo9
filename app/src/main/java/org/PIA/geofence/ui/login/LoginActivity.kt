package org.PIA.geofence.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import org.PIA.geofence.MainActivity
import org.PIA.geofence.R

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private lateinit var btnLogin: Button
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvGoToRegister: TextView
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        // Solo redirigir si existe usuario Y está verificado
        if (currentUser != null && currentUser.isEmailVerified) {
            goToMainActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        window.statusBarColor = getColor(R.color.teal_primary)
        
        btnLogin = findViewById(R.id.btnLogin)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnLogin.isEnabled = false
                    tvError.visibility = View.GONE
                }
                is LoginState.Success -> {
                    progressBar.visibility = View.GONE
                    goToMainActivity()
                }
                is LoginState.Error -> {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    tvError.text = state.message
                    tvError.visibility = View.VISIBLE
                    
                    if (state.canResend) {
                        showResendDialog()
                    }
                }
            }
        }
    }

    private fun showResendDialog() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        
        AlertDialog.Builder(this)
            .setTitle("Correo no verificado")
            .setMessage("¿Deseas que te enviemos un nuevo enlace de verificación?")
            .setPositiveButton("Reenviar") { _, _ ->
                viewModel.resendVerificationEmail(email, password)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
