package org.PIA.geofence.ui.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Por favor completa todos los campos")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _loginState.value = LoginState.Error("El correo no es válido")
            return
        }

        _loginState.value = LoginState.Loading

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Forzamos la recarga del perfil para obtener el estado real de isEmailVerified
                        user.reload().addOnCompleteListener { reloadTask ->
                            if (user.isEmailVerified) {
                                checkUserStatus(user.uid)
                            } else {
                                auth.signOut()
                                _loginState.value = LoginState.Error("Correo no verificado. ¿No recibiste el enlace?", canResend = true)
                            }
                        }
                    } else {
                        _loginState.value = LoginState.Error("Error al obtener información del usuario")
                    }
                } else {
                    val errorMessage = task.exception?.localizedMessage ?: "Error al iniciar sesión"
                    _loginState.value = LoginState.Error(errorMessage)
                }
            }
    }

    fun resendVerificationEmail(email: String, password: String) {
        _loginState.value = LoginState.Loading
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { sendTask ->
                            auth.signOut()
                            if (sendTask.isSuccessful) {
                                _loginState.value = LoginState.Error("Se ha enviado un nuevo correo de verificación.")
                            } else {
                                _loginState.value = LoginState.Error("Error al reenviar: ${sendTask.exception?.localizedMessage}")
                            }
                        }
                } else {
                    _loginState.value = LoginState.Error("Credenciales inválidas para reenviar correo.")
                }
            }
    }

    private fun checkUserStatus(userId: String) {
        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val activo = document.getBoolean("activo") ?: false
                    if (activo) {
                        _loginState.value = LoginState.Success
                    } else {
                        auth.signOut()
                        _loginState.value = LoginState.Error("Tu cuenta está desactivada. Contacta al administrador.")
                    }
                } else {
                    auth.signOut()
                    _loginState.value = LoginState.Error("No se encontró información de tu cuenta.")
                }
            }
            .addOnFailureListener { e ->
                auth.signOut()
                _loginState.value = LoginState.Error("Error al verificar estatus: ${e.localizedMessage}")
            }
    }
}

sealed class LoginState {
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String, val canResend: Boolean = false) : LoginState()
}
