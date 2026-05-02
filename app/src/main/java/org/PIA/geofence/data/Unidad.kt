package org.PIA.geofence.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

data class Unidad(
    @DocumentId
    var id: String = "",
    val placa: String = "",
    val modelo: String = "",
    val estado: String = "Disponible", // Disponible, En ruta
    val ultimaUbicacion: GeoPoint? = null,
    val ultimaActualizacion: Timestamp? = null,
    val numeroEconomico: String = "",
    val choferIdAsignado: String = "",
    val nombreChoferAsignado: String = "",
    val gasolinaActual: Double = 0.0,
    val capacidadMaxima: Double = 100.0,
    val consumoPorKm: Double = 0.12 // Litros por km (Personalizado por unidad)
)
