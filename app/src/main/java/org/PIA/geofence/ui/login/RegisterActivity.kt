package org.PIA.geofence.ui.login

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
import org.PIA.geofence.R
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels()

    private lateinit var etNombre: EditText
    private lateinit var etApellidos: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var btnBack: ImageButton
    private lateinit var tvBackToLogin: TextView
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var tvReqUppercase: TextView
    private lateinit var tvReqLowercase: TextView
    private lateinit var tvReqNumber: TextView
    private lateinit var tvReqMinLength: TextView

    private lateinit var ivShowPassword: ImageView
    private lateinit var ivShowConfirmPassword: ImageView
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    // Variables para flujo de registro
    private var pendingPhone: String = ""
    private var isEmailAuth: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        btnBack = findViewById(R.id.btn_back)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)
        
        ivShowPassword = findViewById(R.id.ivShowPassword)
        ivShowConfirmPassword = findViewById(R.id.ivShowConfirmPassword)

        tvReqUppercase = findViewById(R.id.tvReqUppercase)
        tvReqLowercase = findViewById(R.id.tvReqLowercase)
        tvReqNumber = findViewById(R.id.tvReqNumber)
        tvReqMinLength = findViewById(R.id.tvReqMinLength)

        ivShowPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(etPassword, ivShowPassword, isPasswordVisible)
        }

        ivShowConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(etConfirmPassword, ivShowConfirmPassword, isConfirmPasswordVisible)
        }

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
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPass = etConfirmPassword.text.toString().trim()
            viewModel.register(nombre, apellidos, phone, email, password, confirmPass)
        }

        btnBack.setOnClickListener {
            finish()
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
                    this.isEmailAuth = state.isEmailAuth
                    this.pendingPhone = state.phone

                    if (state.phone.isNotBlank()) {
                        startPhoneVerification(state.phone)
                    } else {
                        // Solo correo, proceso terminado (ya se envió verificación en VM)
                        Toast.makeText(this, "Registro exitoso. Revisa tu correo.", Toast.LENGTH_LONG).show()
                        finish()
                    }
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

    private fun startPhoneVerification(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder()
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    handleCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@RegisterActivity, "Error de SMS: ${e.message}", Toast.LENGTH_LONG).show()
                    btnRegister.isEnabled = true
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    viewModel.setVerificationInfo(verificationId, token)
                    showOtpDialog()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun showOtpDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "123456"

        AlertDialog.Builder(this)
            .setTitle("Verificación de Teléfono")
            .setMessage("Ingresa el código enviado a " + pendingPhone)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Verificar") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length == 6) {
                    val verificationId = viewModel.getVerificationId() ?: return@setPositiveButton
                    val credential = PhoneAuthProvider.getCredential(verificationId, code)
                    handleCredential(credential)
                } else {
                    Toast.makeText(this, "Código incompleto", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun handleCredential(credential: PhoneAuthCredential) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (isEmailAuth && user != null) {
            // Caso A: Ya hay usuario por correo, lo vinculamos
            user.linkWithCredential(credential).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Registro y teléfono vinculados correctamente", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    finish()
                } else {
                    Toast.makeText(this, "Error al vincular: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    btnRegister.isEnabled = true
                }
            }
        } else {
            // Caso B: Solo teléfono, iniciamos sesión normal
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    if (firebaseUser != null) {
                        // Guardar datos en Firestore ya que es un usuario nuevo sin Email
                        viewModel.saveUserToFirestore(
                            firebaseUser.uid,
                            etNombre.text.toString().trim(),
                            etApellidos.text.toString().trim(),
                            "",
                            pendingPhone
                        ) {
                            Toast.makeText(this, "Registro por teléfono exitoso", Toast.LENGTH_LONG).show()
                            auth.signOut()
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this, "Error de inicio de sesión: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    btnRegister.isEnabled = true
                }
            }
        }
    }

    private fun validatePasswordRequirements(password: String) {
        updateRequirementState(tvReqUppercase, password.any { it.isUpperCase() })
        updateRequirementState(tvReqLowercase, password.any { it.isLowerCase() })
        updateRequirementState(tvReqNumber, password.any { it.isDigit() })
        updateRequirementState(tvReqMinLength, password.length >= 6)
    }

    private fun updateRequirementState(textView: TextView, isMet: Boolean) {
        if (isMet) {
            textView.setTextColor(Color.parseColor("#2E7D32"))
            if (!textView.text.startsWith("✓")) {
                textView.text = "✓ " + textView.text.toString().removePrefix("• ")
            }
        } else {
            textView.setTextColor(Color.parseColor("#666666"))
            if (textView.text.startsWith("✓")) {
                textView.text = "• " + textView.text.toString().removePrefix("✓ ")
            }
        }
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
}
