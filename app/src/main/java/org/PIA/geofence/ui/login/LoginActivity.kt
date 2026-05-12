package org.PIA.geofence.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import org.PIA.geofence.MainActivity
import org.PIA.geofence.R
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private lateinit var btnLogin: Button
    private lateinit var etIdentifier: EditText
    private lateinit var etPassword: EditText
    private lateinit var layoutPassword: LinearLayout
    private lateinit var ivShowPassword: ImageView
    private lateinit var tvGoToRegister: TextView
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar
    
    private var isPasswordVisible = false

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Si el usuario ya está logueado, vamos al main
            // (Nota: quitamos isEmailVerified por si entra solo por teléfono)
            goToMainActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        btnLogin = findViewById(R.id.btnLogin)
        etIdentifier = findViewById(R.id.etIdentifier)
        etPassword = findViewById(R.id.etPassword)
        layoutPassword = findViewById(R.id.layoutPassword)
        ivShowPassword = findViewById(R.id.ivShowPassword)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)

        // Detectar si es teléfono o correo en tiempo real
        etIdentifier.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString().trim()
                if (input.startsWith("+")) {
                    layoutPassword.visibility = View.GONE
                } else {
                    layoutPassword.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        ivShowPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(etPassword, ivShowPassword, isPasswordVisible)
        }

        btnLogin.setOnClickListener {
            val identifier = etIdentifier.text.toString().trim()
            if (identifier.startsWith("+")) {
                startPhoneLogin(identifier)
            } else {
                val password = etPassword.text.toString().trim()
                viewModel.login(identifier, password)
            }
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
                else -> {}
            }
        }
    }

    private fun startPhoneLogin(phoneNumber: String) {
        if (phoneNumber.length < 10) {
            tvError.text = "Ingresa un número de teléfono válido"
            tvError.visibility = View.VISIBLE
            return
        }

        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false

        val options = PhoneAuthOptions.newBuilder()
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    viewModel.signInWithPhone(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    tvError.text = "Error de verificación: ${e.message}"
                    tvError.visibility = View.VISIBLE
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    viewModel.setVerificationInfo(verificationId, token)
                    showOtpDialog(phoneNumber)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun showOtpDialog(phone: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "123456"

        AlertDialog.Builder(this)
            .setTitle("Verificación de Teléfono")
            .setMessage("Ingresa el código enviado a $phone")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Verificar") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length == 6) {
                    val verificationId = viewModel.getVerificationId() ?: return@setPositiveButton
                    val credential = PhoneAuthProvider.getCredential(verificationId, code)
                    viewModel.signInWithPhone(credential)
                } else {
                    Toast.makeText(this, "Código inválido", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean) {
        if (isVisible) {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imageView.alpha = 1.0f 
        } else {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imageView.alpha = 0.5f
        }
        editText.setSelection(editText.text.length)
    }

    private fun showResendDialog() {
        val email = etIdentifier.text.toString().trim()
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
