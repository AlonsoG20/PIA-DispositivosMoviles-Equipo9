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
    val estado: String = "pendiente", // "pendiente", "guardada", "completada", "descartada"
    val fechaCreacion: Timestamp? = null,
    val fechaCompletado: Timestamp? = null,
    val primeraModificacion: Timestamp? = null,
    val bloqueado: Int = 0 // 0 = no, 1 = si
)
