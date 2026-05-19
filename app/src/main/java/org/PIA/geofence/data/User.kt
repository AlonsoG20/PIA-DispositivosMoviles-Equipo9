package org.PIA.geofence.data

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    var id: String = "",
    val nombre: String? = null,
    val apellidos: String? = null,
    val email: String? = null,
    val telefono: String? = null,
    val rol: String? = null,
    val activo: Boolean? = null,
    val estado: String? = "0", // "0" = Disponible, "1" = Ocupado
    val unidad: String? = null
) {
    val nombreCompleto: String get() = "${nombre ?: ""} ${apellidos ?: ""}".trim().ifEmpty { "Sin nombre" }
    val emailSeguro: String get() = email ?: "Sin correo"
    val telefonoSeguro: String get() = telefono ?: "Sin teléfono"
}