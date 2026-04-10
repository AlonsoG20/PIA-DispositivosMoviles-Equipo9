package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Viaje(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val titulo: String = "",
    val distancia: String = "0.0",
    val paradas: Int = 0,
    val fecha: Timestamp? = null,
    val costo: String = "0",
    val combustible: String = "0.0"
)