package org.PIA.geofence.ui.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Esta clase maneja la lógica del login, separada de la pantalla
class LoginViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Estado del login que la pantalla puede observar
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(email: String, password: String) {

        // Validamos que los campos no estén vacíos
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Por favor completa todos los campos")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _loginState.value = LoginState.Error("El correo no es válido")
            return
        }

        // Mostramos loading mientras esperamos
        _loginState.value = LoginState.Loading

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        checkUserStatus(userId)
                    } else {
                        _loginState.value = LoginState.Error("Error al obtener ID de usuario")
                    }
                } else {
                    val errorMessage = task.exception?.localizedMessage ?: "Error al iniciar sesión"
                    _loginState.value = LoginState.Error(errorMessage)
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
                    _loginState.value = LoginState.Error("No se encontró información de tu cuenta. Es posible que haya sido eliminada.")
                }
            }
            .addOnFailureListener { e ->
                auth.signOut()
                _loginState.value = LoginState.Error("Error al verificar estatus: ${e.localizedMessage}")
            }
    }
}

// Posibles estados del login
sealed class LoginState {
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}