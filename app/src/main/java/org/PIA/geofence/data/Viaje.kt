package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

data class Viaje(
    @DocumentId
    val id: String = "",
    val estado: String = "",
    val titulo: String = "", // Nombre del viaje
    val choferId: String = "", // ID del chofer
    val nombreChofer: String = "",
    val despachadorId: String = "", // ID del despachador que asignó
    val nombreDespachador: String = "",
    val fechaInicio: Timestamp? = null,
    val fechaFin: Timestamp? = null,
    val unidadId: String = "", // ID de la unidad
    val placaUnidad: String = "", // Placa de la unidad
    val distancia: String = "0.0", //distancia del trayecto
    val combustible: String = "0.0", //
    val costo: String = "0", //costo aproximado del viaje
    val cantidadParadas: Int = 0, //antes "paradas"
    val puntosParada: List<GeoPoint> = emptyList() //Ubicación de las paradas del viaje
)
