package org.PIA.geofence.ui.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

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
                        user.reload().addOnCompleteListener { reloadTask ->
                            if (user.isEmailVerified) {
                                checkUserStatus(user.uid)
                            } else {
                                auth.signOut()
                                _loginState.value = LoginState.Error("Correo no verificado.", canResend = true)
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

    fun signInWithPhone(credential: PhoneAuthCredential) {
        _loginState.value = LoginState.Loading
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        checkUserStatus(user.uid)
                    } else {
                        _loginState.value = LoginState.Error("Error al obtener información del usuario")
                    }
                } else {
                    _loginState.value = LoginState.Error(task.exception?.localizedMessage ?: "Error de verificación")
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
                    _loginState.value = LoginState.Error("Credenciales inválidas.")
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
                        _loginState.value = LoginState.Error("Tu cuenta está desactivada.")
                    }
                } else {
                    // Si el usuario existe en Auth pero no en Firestore, podría ser un error de registro incompleto
                    _loginState.value = LoginState.Success
                }
            }
            .addOnFailureListener { e ->
                auth.signOut()
                _loginState.value = LoginState.Error("Error al verificar estatus: ${e.localizedMessage}")
            }
    }

    fun setVerificationInfo(id: String, token: PhoneAuthProvider.ForceResendingToken?) {
        this.verificationId = id
        this.resendToken = token
    }

    fun getVerificationId() = verificationId
}

sealed class LoginState {
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String, val canResend: Boolean = false) : LoginState()
    data class CodeSent(val phone: String) : LoginState()
}
