package org.PIA.geofence.data

import com.google.firebase.firestore.GeoPoint

data class PuntoInteres(
    var id: String = "",
    val nombre: String = "",
    val lugar: GeoPoint? = null
)