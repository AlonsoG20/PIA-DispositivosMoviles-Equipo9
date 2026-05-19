package org.PIA.geofence.ui.cuenta

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import org.PIA.geofence.data.User

class UserViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadUser(userId: String, forceRefresh: Boolean = false) {
        if (_userData.value != null && !forceRefresh) return

        _loading.value = true
        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val user = document.toObject(User::class.java)?.apply { id = document.id }
                    _userData.value = user
                    _error.value = null
                } else {
                    _error.value = "Usuario no encontrado"
                }
                _loading.value = false
            }
            .addOnFailureListener { e ->
                _error.value = e.message
                _loading.value = false
            }
    }

    fun clear() {
        _userData.value = null
    }
}