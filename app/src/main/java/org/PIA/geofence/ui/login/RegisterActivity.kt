package org.PIA.geofence.ui.login

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.PIA.geofence.R

class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels()

    private lateinit var etNombre: EditText
    private lateinit var etApellidos: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvBackToLogin: TextView
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar

    // Requisitos de contraseña
    private lateinit var tvReqUppercase: TextView
    private lateinit var tvReqLowercase: TextView
    private lateinit var tvReqNumber: TextView
    private lateinit var tvReqMinLength: TextView

    // Iconos de visibilidad
    private lateinit var ivShowPassword: ImageView
    private lateinit var ivShowConfirmPassword: ImageView
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Se comenta debido a que ahora la app es fullscreen desde el tema
        // window.statusBarColor = getColor(R.color.teal_primary)

        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)
        
        ivShowPassword = findViewById(R.id.ivShowPassword)
        ivShowConfirmPassword = findViewById(R.id.ivShowConfirmPassword)

        // Inicializar requisitos
        tvReqUppercase = findViewById(R.id.tvReqUppercase)
        tvReqLowercase = findViewById(R.id.tvReqLowercase)
        tvReqNumber = findViewById(R.id.tvReqNumber)
        tvReqMinLength = findViewById(R.id.tvReqMinLength)

        // Configurar toggle para contraseña principal
        ivShowPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(etPassword, ivShowPassword, isPasswordVisible)
        }

        // Configurar toggle para confirmar contraseña
        ivShowConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(etConfirmPassword, ivShowConfirmPassword, isConfirmPasswordVisible)
        }

        // Listener para validar requisitos en tiempo real
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePasswordRequirements(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnRegister.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val apellidos = etApellidos.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPass = etConfirmPassword.text.toString().trim()
            viewModel.register(nombre, apellidos, email, password, confirmPass)
        }

        tvBackToLogin.setOnClickListener {
            finish()
        }

        viewModel.registerState.observe(this) { state ->
            when (state) {
                is RegisterState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnRegister.isEnabled = false
                    tvError.visibility = View.GONE
                }
                is RegisterState.Success -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Registro exitoso. Por favor verifica tu correo electrónico.", Toast.LENGTH_LONG).show()
                    finish()
                }
                is RegisterState.Error -> {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    tvError.text = state.message
                    tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun validatePasswordRequirements(password: String) {
        // Mayúscula
        updateRequirementState(tvReqUppercase, password.any { it.isUpperCase() })
        // Minúscula
        updateRequirementState(tvReqLowercase, password.any { it.isLowerCase() })
        // Número
        updateRequirementState(tvReqNumber, password.any { it.isDigit() })
        // Longitud mínima
        updateRequirementState(tvReqMinLength, password.length >= 6)
    }

    private fun updateRequirementState(textView: TextView, isMet: Boolean) {
        if (isMet) {
            textView.setTextColor(Color.parseColor("#2E7D32")) // Verde
            // Opcional: añadir un check al inicio si se desea, por ahora solo cambio de color
            if (!textView.text.startsWith("✓")) {
                textView.text = "✓ " + textView.text.toString().removePrefix("• ")
            }
        } else {
            textView.setTextColor(Color.parseColor("#666666")) // Gris original
            if (textView.text.startsWith("✓")) {
                textView.text = "• " + textView.text.toString().removePrefix("✓ ")
            }
        }
    }

    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean) {
        if (isVisible) {
            // Mostrar contraseña
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imageView.alpha = 1.0f 
        } else {
            // Ocultar contraseña
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imageView.alpha = 0.5f
        }
        // Mover el cursor al final del texto después del cambio
        editText.setSelection(editText.text.length)
    }
}
