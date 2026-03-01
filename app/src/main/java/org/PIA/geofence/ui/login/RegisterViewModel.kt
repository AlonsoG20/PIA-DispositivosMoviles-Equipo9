package org.PIA.geofence.ui.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _registerState = MutableLiveData<RegisterState>()
    val registerState: LiveData<RegisterState> = _registerState

    fun register(nombre: String, apellidos: String, email: String, password: String, confirmPass: String) {
        if (nombre.isBlank() || apellidos.isBlank() || email.isBlank() || password.isBlank() || confirmPass.isBlank()) {
            _registerState.value = RegisterState.Error("Por favor completa todos los campos")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _registerState.value = RegisterState.Error("El correo no es válido")
            return
        }

        if (password.length < 6) {
            _registerState.value = RegisterState.Error("La contraseña debe tener al menos 6 caracteres")
            return
        }

        if (password != confirmPass) {
            _registerState.value = RegisterState.Error("Las contraseñas no coinciden")
            return
        }

        _registerState.value = RegisterState.Loading

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    
                    // 1. Enviar correo de verificación
                    firebaseUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                val userId = firebaseUser.uid
                                // 2. Guardar datos en Firestore
                                val user = hashMapOf(
                                    "nombre" to nombre,
                                    "apellidos" to apellidos,
                                    "email" to email,
                                    "rol" to "sinRol",
                                    "activo" to true,
                                    "createdAt" to System.currentTimeMillis()
                                )
                                
                                db.collection("usuarios").document(userId).set(user)
                                    .addOnSuccessListener {
                                        auth.signOut()
                                        _registerState.value = RegisterState.Success
                                    }
                                    .addOnFailureListener { e ->
                                        _registerState.value = RegisterState.Error("Usuario creado, pero hubo un error en Firestore: ${e.localizedMessage}")
                                    }
                            } else {
                                _registerState.value = RegisterState.Error("Error al enviar el correo de verificación: ${verificationTask.exception?.localizedMessage}")
                            }
                        }
                } else {
                    val errorMessage = task.exception?.localizedMessage ?: "Error al registrarse"
                    _registerState.value = RegisterState.Error(errorMessage)
                }
            }
    }
}

sealed class RegisterState {
    object Loading : RegisterState()
    object Success : RegisterState()
    data class Error(val message: String) : RegisterState()
}
