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
import androidx.appcompat.app.AppCompatActivity
import org.PIA.geofence.MainActivity
import org.PIA.geofence.R

class LoginActivity : AppCompatActivity() {

    // Creamos el ViewModel ligado a esta pantalla
    private val viewModel: LoginViewModel by viewModels()

    // Referencias a los elementos visuales del layout
    private lateinit var btnLogin: Button
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvGoToRegister: TextView
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        // Hace que la barra de estado sea transparente y quita los márgenes blancos
        window.statusBarColor = getColor(R.color.teal_primary)
        // Conectamos las variables con los elementos del layout usando su ID
        btnLogin = findViewById(R.id.btnLogin)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)

        // Cuando el usuario toca el botón de login
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        // Cuando el usuario toca "Registrarte aquí"
        tvGoToRegister.setOnClickListener {
            // TODO: Navegar a pantalla de registro
            Toast.makeText(this, "Ir a Registro", Toast.LENGTH_SHORT).show()
        }

        // Observamos los cambios de estado que manda el ViewModel
        viewModel.loginState.observe(this) { state ->
            when (state) {

                is LoginState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnLogin.isEnabled = false
                    tvError.visibility = View.GONE
                }

                is LoginState.Success -> {
                    progressBar.visibility = View.GONE
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }

                is LoginState.Error -> {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    tvError.text = state.message
                    tvError.visibility = View.VISIBLE
                }
            }
        }
    }
}