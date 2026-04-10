package org.PIA.geofence.data

import com.google.firebase.firestore.DocumentId

data class Unidad(
    @DocumentId
    val id: String = "",
    val placa: String = "",
    val modelo: String = "",
    val estado: String = "Disponible" // Disponible, En ruta, Mantenimiento
)