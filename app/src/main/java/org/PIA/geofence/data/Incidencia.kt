package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Incidencia(
    @DocumentId
    val id: String = "",
    val chofer: String = "",
    val tipo: String = "", // "Parada saltada", "Zona no permitida", etc.
    val detalle: String = "",
    val fecha: Timestamp? = null,
    val nivelPrioridad: String = "Baja" // "Baja", "Media", "Alta"
)