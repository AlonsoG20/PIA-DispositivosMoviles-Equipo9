package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

data class Unidad(
    @DocumentId
    val id: String = "",
    val placa: String = "",
    val modelo: String = "",
    val estado: String = "Disponible", // Disponible, En ruta
    val ultimaUbicacion: GeoPoint? = null,
    val ultimaActualizacion: Timestamp? = null,
    val numeroEconomico: String = "",
    val choferIdAsignado: String = "", // Id del chofer
    val nombreChoferAsignado: String = "" // Nombre del chofer
)