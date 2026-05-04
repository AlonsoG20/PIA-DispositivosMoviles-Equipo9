package org.PIA.geofence.ui.rutas

import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import org.PIA.geofence.data.Ruta

class RutasViewModel : ViewModel() {
    var rutaAsignada: Ruta? = null
    var rutaIniciada: Boolean = false
    var paradasCompletadas: Int = 0
    var ultimoIndiceRecorrido: Int = 0
    var puntosRutaRapida: List<LatLng> = emptyList()
    var puntosRutaAlterna: List<LatLng> = emptyList()
    var puntosCaminoSeleccionado: List<LatLng> = emptyList()
    var esRutaRapidaSeleccionada: Boolean = true
    var kmRutaRapida: Double = 0.0
    var kmRutaAlterna: Double = 0.0
}
