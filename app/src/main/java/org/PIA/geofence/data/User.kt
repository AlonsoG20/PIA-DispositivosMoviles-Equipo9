package org.PIA.geofence.data

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    val id: String = "",
    val nombre: String? = null,
    val apellidos: String? = null,
    val email: String? = null,
    val rol: String? = null,
    val activo: Boolean? = null
) {
    // Propiedades calculadas seguras para evitar errores de nulos en la UI
    val nombreCompleto: String get() = "${nombre ?: ""} ${apellidos ?: ""}".trim().ifEmpty { "Sin nombre" }
    val emailSeguro: String get() = email ?: "Sin correo"
}