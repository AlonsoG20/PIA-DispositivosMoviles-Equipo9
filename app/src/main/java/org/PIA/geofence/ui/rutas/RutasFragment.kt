package org.PIA.geofence.ui.rutas

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import org.PIA.geofence.R
import org.PIA.geofence.ReceptorGeofence
import org.PIA.geofence.data.Viaje
import java.text.SimpleDateFormat
import java.util.*

class RutasFragment : Fragment(R.layout.fragment_rutas), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var btnIniciarRuta: Button
    private lateinit var tvEstadoRuta: TextView
    private lateinit var tvParadaActual: TextView
    private lateinit var tvParadasCompletadas: TextView
    private lateinit var toggleRouteGroup: MaterialButtonToggleGroup

    private var rutaIniciada = false
    private var paradasCompletadas = 0
    private var viajeActivo: Viaje? = null
    private var viajeListener: ListenerRegistration? = null
    
    private var markerVehiculo: Marker? = null
    private var poliLineaRapida: Polyline? = null
    private var poliLineaAlterna: Polyline? = null
    private var animatorVehiculo: ValueAnimator? = null
    
    private var puntosRutaRapida = mutableListOf<LatLng>()
    private var puntosRutaAlterna = mutableListOf<LatLng>()
    private var puntosCaminoSeleccionado = mutableListOf<LatLng>()

    private val apiKey = "AIzaSyAjjiNfvcx-3pSKpNULTceVeX46YxLfItc"
    
    // Coordenadas por defecto (Monterrey)
    private val MONTERREY = LatLng(25.6866, -100.3161)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnIniciarRuta = view.findViewById(R.id.btnIniciarRuta)
        tvEstadoRuta = view.findViewById(R.id.tvEstadoRuta)
        tvParadaActual = view.findViewById(R.id.tvParadaActual)
        tvParadasCompletadas = view.findViewById(R.id.tvParadasCompletadas)
        toggleRouteGroup = view.findViewById(R.id.toggleRouteGroup)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        geofencingClient = LocationServices.getGeofencingClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnIniciarRuta.setOnClickListener {
            if (!rutaIniciada) {
                iniciarRuta()
            } else {
                terminarRuta(true)
            }
        }

        toggleRouteGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !rutaIniciada) {
                puntosCaminoSeleccionado = if (checkedId == R.id.btnRouteA) puntosRutaRapida else puntosRutaAlterna
                actualizarEstiloRutas()
            }
        }
    }

    private fun actualizarEstiloRutas() {
        if (puntosCaminoSeleccionado == puntosRutaRapida) {
            poliLineaRapida?.apply {
                zIndex = 10f
                color = Color.parseColor("#4CAF9E") // Teal/Verde
                width = 18f
            }
            poliLineaAlterna?.apply {
                zIndex = 5f
                color = Color.LTGRAY
                width = 12f
            }
        } else {
            poliLineaAlterna?.apply {
                zIndex = 10f
                color = Color.parseColor("#2196F3") // Blue
                width = 18f
            }
            poliLineaRapida?.apply {
                zIndex = 5f
                color = Color.LTGRAY
                width = 12f
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // Configuración inicial: Monterrey
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(MONTERREY, 12f))
        
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
        
        escucharViajeAsignado()
    }

    private fun escucharViajeAsignado() {
        val userId = auth.currentUser?.uid ?: return
        
        // Buscamos viajes activos asignados a ESTE chofer
        viajeListener = db.collection("viajes")
            .whereEqualTo("userId", userId)
            .whereEqualTo("fechaFin", null)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(context, "Error al cargar viaje: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val viaje = snapshot?.toObjects(Viaje::class.java)?.firstOrNull()
                
                if (viaje != null) {
                    // Si es un nuevo viaje o cambió algo, lo preparamos
                    if (viajeActivo?.id != viaje.id && !rutaIniciada) {
                        viajeActivo = viaje
                        prepararRutaDesdeViaje(viaje)
                    }
                } else {
                    viajeActivo = null
                    mMap.clear()
                    tvEstadoRuta.text = "Sin viaje asignado"
                    btnIniciarRuta.isEnabled = false
                    toggleRouteGroup.visibility = View.GONE
                }
            }
    }

    private fun prepararRutaDesdeViaje(viaje: Viaje) {
        mMap.clear()
        
        // Convertimos GeoPoints de Firebase a LatLng de Maps
        val paradasLatLng = viaje.puntosParada.map { LatLng(it.latitude, it.longitude) }
        
        if (paradasLatLng.isEmpty()) {
            tvEstadoRuta.text = "Error: El viaje no tiene paradas registradas"
            return
        }

        // Añadir marcadores para cada parada
        paradasLatLng.forEachIndexed { index, pos ->
            val title = if (index == 0) "Inicio" else if (index == paradasLatLng.size - 1) "Destino Final" else "Parada ${index + 1}"
            mMap.addMarker(MarkerOptions()
                .position(pos)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(if (index == 0) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_CYAN)))
        }

        // Trazar las dos opciones de camino
        calcularYMostrarCaminos(paradasLatLng)
        
        // Enfocar la cámara en la primera parada
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(paradasLatLng[0], 14f))
        
        btnIniciarRuta.isEnabled = true
        tvEstadoRuta.text = "Viaje: ${viaje.titulo}"
        tvParadasCompletadas.text = "0/${paradasLatLng.size} paradas"
    }

    private fun calcularYMostrarCaminos(paradas: List<LatLng>) {
        val geoContext = GeoApiContext.Builder().apiKey(apiKey).build()
        
        val origin = "${paradas.first().latitude},${paradas.first().longitude}"
        val destination = "${paradas.last().latitude},${paradas.last().longitude}"
        
        // Waypoints intermedios (excluyendo el primero y el último)
        val waypoints = if (paradas.size > 2) {
            paradas.subList(1, paradas.size - 1).map { 
                com.google.maps.model.LatLng(it.latitude, it.longitude) 
            }.toTypedArray()
        } else {
            emptyArray()
        }

        Thread {
            try {
                val result = DirectionsApi.newRequest(geoContext)
                    .mode(TravelMode.DRIVING)
                    .origin(origin)
                    .destination(destination)
                    .waypoints(*waypoints)
                    .alternatives(true) // Crucial para ver dos opciones
                    .await()

                if (result.routes.isNotEmpty()) {
                    activity?.runOnUiThread {
                        // Limpiar polilíneas anteriores si existen
                        poliLineaRapida?.remove()
                        poliLineaAlterna?.remove()

                        // Ruta 1: Principal
                        puntosRutaRapida = result.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }.toMutableList()
                        poliLineaRapida = mMap.addPolyline(PolylineOptions()
                            .addAll(puntosRutaRapida)
                            .jointType(JointType.ROUND))
                        
                        // Ruta 2: Alternativa
                        if (result.routes.size > 1) {
                            puntosRutaAlterna = result.routes[1].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }.toMutableList()
                            poliLineaAlterna = mMap.addPolyline(PolylineOptions()
                                .addAll(puntosRutaAlterna)
                                .jointType(JointType.ROUND))
                        } else {
                            puntosRutaAlterna = puntosRutaRapida
                        }

                        // Por defecto seleccionamos la rápida
                        puntosCaminoSeleccionado = puntosRutaRapida
                        actualizarEstiloRutas()
                        
                        toggleRouteGroup.visibility = View.VISIBLE
                        toggleRouteGroup.check(R.id.btnRouteA)
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread { 
                    Toast.makeText(requireContext(), "Error de mapa: ${e.message}", Toast.LENGTH_SHORT).show() 
                }
            }
        }.start()
    }

    private fun iniciarRuta() {
        if (puntosCaminoSeleccionado.isEmpty()) {
            Toast.makeText(requireContext(), "Espera a que se cargue el camino", Toast.LENGTH_SHORT).show()
            return
        }
        
        rutaIniciada = true
        paradasCompletadas = 0
        toggleRouteGroup.visibility = View.GONE

        btnIniciarRuta.text = "Terminar Ruta"
        tvEstadoRuta.text = "En curso: ${viajeActivo?.titulo}"
        
        // El vehículo empieza en el inicio del camino seleccionado
        markerVehiculo = mMap.addMarker(MarkerOptions()
            .position(puntosCaminoSeleccionado[0])
            .title("Mi Ubicación")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            .anchor(0.5f, 0.5f)
            .zIndex(15f))

        animarVehiculoPorCamino(0)
    }

    private fun animarVehiculoPorCamino(pointIndex: Int) {
        if (!rutaIniciada || pointIndex >= puntosCaminoSeleccionado.size - 1) return
        
        val inicio = puntosCaminoSeleccionado[pointIndex]
        val fin = puntosCaminoSeleccionado[pointIndex + 1]

        animatorVehiculo = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600 // Velocidad de la simulación
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val lat = (fin.latitude - inicio.latitude) * fraction + inicio.latitude
                val lng = (fin.longitude - inicio.longitude) * fraction + inicio.longitude
                val currentPos = LatLng(lat, lng)
                
                markerVehiculo?.position = currentPos
                mMap.moveCamera(CameraUpdateFactory.newLatLng(currentPos))
                verificarLlegadaAParada(currentPos)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (rutaIniciada) animarVehiculoPorCamino(pointIndex + 1)
                }
            })
            start()
        }
    }

    private fun verificarLlegadaAParada(pos: LatLng) {
        val paradas = viajeActivo?.puntosParada ?: return
        paradas.forEachIndexed { index, parada ->
            if (index >= paradasCompletadas) {
                val distance = FloatArray(1)
                android.location.Location.distanceBetween(pos.latitude, pos.longitude, parada.latitude, parada.longitude, distance)
                if (distance[0] < 70) { // Radio de 70 metros para detectar llegada
                    onParadaAlcanzada(index)
                }
            }
        }
    }

    private fun onParadaAlcanzada(index: Int) {
        if (index < paradasCompletadas) return
        paradasCompletadas = index + 1
        tvParadasCompletadas.text = "$paradasCompletadas/${viajeActivo?.puntosParada?.size ?: 0} completadas"
        
        if (paradasCompletadas >= (viajeActivo?.puntosParada?.size ?: 0)) {
            Toast.makeText(requireContext(), "¡Has llegado a tu destino final!", Toast.LENGTH_LONG).show()
            terminarRuta(true)
        } else {
            tvParadaActual.text = "Próxima: Parada ${index + 2}"
        }
    }

    private fun terminarRuta(guardar: Boolean) {
        rutaIniciada = false
        animatorVehiculo?.cancel()
        markerVehiculo?.remove()
        
        if (guardar && viajeActivo != null) {
            db.collection("viajes").document(viajeActivo!!.id).update("fechaFin", com.google.firebase.Timestamp.now())
        }
        
        btnIniciarRuta.text = "Iniciar Ruta"
        tvEstadoRuta.text = "Viaje Finalizado"
        toggleRouteGroup.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viajeListener?.remove()
        animatorVehiculo?.cancel()
    }
}
