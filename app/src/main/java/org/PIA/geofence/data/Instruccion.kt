package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Instruccion(
    @DocumentId
    var id: String = "",
    val mensaje: String = "",
    val remitenteId: String = "",
    val remitenteNombre: String = "",
    val destinatarioId: String = "",
    val estado: String = "pendiente", // "pendiente", "completada"
    val fechaCreacion: Timestamp = Timestamp.now(),
    val fechaCompletado: Timestamp? = null
)
