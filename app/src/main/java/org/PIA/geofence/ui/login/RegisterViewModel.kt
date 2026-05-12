package org.PIA.geofence.ui.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _registerState = MutableLiveData<RegisterState>()
    val registerState: LiveData<RegisterState> = _registerState

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun register(nombre: String, apellidos: String, phone: String, email: String, password: String, confirmPass: String) {
        // Validar campos básicos
        if (nombre.isBlank() || apellidos.isBlank()) {
            _registerState.value = RegisterState.Error("El nombre y apellidos son obligatorios")
            return
        }

        // Validar que al menos uno exista
        if (email.isBlank() && phone.isBlank()) {
            _registerState.value = RegisterState.Error("Debes ingresar un correo o un número de teléfono")
            return
        }

        // Si hay correo, la contraseña es obligatoria
        if (email.isNotBlank() && (password.isBlank() || confirmPass.isBlank())) {
            _registerState.value = RegisterState.Error("La contraseña es obligatoria para el registro por correo")
            return
        }

        if (password != confirmPass) {
            _registerState.value = RegisterState.Error("Las contraseñas no coinciden")
            return
        }

        _registerState.value = RegisterState.Loading

        if (email.isNotBlank()) {
            // ESCENARIO A: Registro con Email (y opcionalmente teléfono)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        firebaseUser?.sendEmailVerification()
                        
                        val userId = firebaseUser?.uid ?: ""
                        saveUserToFirestore(userId, nombre, apellidos, email, phone) {
                            _registerState.value = RegisterState.Success(phone, true)
                        }
                    } else {
                        _registerState.value = RegisterState.Error(task.exception?.localizedMessage ?: "Error al registrar")
                    }
                }
        } else {
            // ESCENARIO B: Solo Teléfono
            // No creamos el usuario aún, primero debemos verificar el teléfono en la Activity
            _registerState.value = RegisterState.Success(phone, false)
        }
    }

    fun saveUserToFirestore(uid: String, nombre: String, apellidos: String, email: String, phone: String, onComplete: () -> Unit) {
        val user = hashMapOf(
            "nombre" to nombre,
            "apellidos" to apellidos,
            "telefono" to phone,
            "email" to email,
            "rol" to "sinRol",
            "activo" to true,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("usuarios").document(uid).set(user)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { e ->
                _registerState.value = RegisterState.Error("Error en base de datos: ${e.localizedMessage}")
            }
    }

    fun setVerificationInfo(id: String, token: PhoneAuthProvider.ForceResendingToken?) {
        this.verificationId = id
        this.resendToken = token
    }

    fun getVerificationId() = verificationId
}

sealed class RegisterState {
    object Loading : RegisterState()
    data class Success(val phone: String, val isEmailAuth: Boolean) : RegisterState()
    data class Error(val message: String) : RegisterState()
}
