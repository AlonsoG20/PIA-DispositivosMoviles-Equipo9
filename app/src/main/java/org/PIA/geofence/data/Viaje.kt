package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

data class Viaje(
    @DocumentId
    val id: String = "",
    val userId: String = "", // ID del chofer
    val unidadId: String = "", // ID de la unidad
    val despachadorId: String = "", // ID del despachador que asignó
    val titulo: String = "",
    val distancia: String = "0.0",
    val paradas: Int = 0,
    val fecha: Timestamp? = null, 
    val fechaInicio: Timestamp? = null,
    val fechaFin: Timestamp? = null,
    val costo: String = "0",
    val combustible: String = "0.0",
    val placaUnidad: String = "",
    val nombreChofer: String = "",
    // Para las paradas dinámicas
    val puntosParada: List<GeoPoint> = emptyList()
)
