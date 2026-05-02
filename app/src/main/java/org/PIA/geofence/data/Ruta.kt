package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Ruta(
    @DocumentId
    var id: String = "",
    val nombre: String = "",
    val choferId: String? = null,
    val choferNombre: String? = null,
    val despachadorId: String = "",
    val despachadorNombre: String? = null,
    val unidadId: String? = null,
    val unidadPlaca: String? = null,
    val unidadEco: String? = null,
    val estado: String = "pendiente", // "pendiente", "aceptada", "en_progreso", "completada"
    val fechaCreacion: Timestamp? = null,
    val paradas: List<ParadaData> = emptyList()
)

data class ParadaData(
    val nombre: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0
)
