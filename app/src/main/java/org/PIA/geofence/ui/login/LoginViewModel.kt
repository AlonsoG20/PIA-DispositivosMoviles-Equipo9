package org.PIA.geofence.ui.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Esta clase maneja la lógica del login, separada de la pantalla
class LoginViewModel : ViewModel() {

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

        // TODO: Reemplazar con llamada a Firebase Auth
        viewModelScope.launch {
            delay(1500) // Simulamos tiempo de espera de red

            // mientras no este la base de datos
            _loginState.value = LoginState.Success
            /*if (email.contains("test")) {
                _loginState.value = LoginState.Success
            } else {
                _loginState.value = LoginState.Error("Correo o contraseña incorrectos")
            }*/
        }
    }
}

// Posibles estados del login
sealed class LoginState {
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}