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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import org.PIA.geofence.R
import org.PIA.geofence.ReceptorGeofence
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

    private var rutaIniciada = false
    private var paradasCompletadas = 0
    
    private var markerVehiculo: Marker? = null
    private var poliLineaRuta: Polyline? = null
    private var animatorVehiculo: ValueAnimator? = null
    
    private var puntosCaminoReal = mutableListOf<LatLng>()

    // NUEVA API KEY - Asegurada en ambos lugares
    private val apiKey = "AIzaSyAjjiNfvcx-3pSKpNULTceVeX46YxLfItc"

    private val paradas = listOf(
        Triple("Parada 1", 25.6866, -100.3161),
        Triple("Parada 2", 25.6900, -100.3200),
        Triple("Parada 3", 25.6950, -100.3250),
        Triple("Parada 4", 25.6980, -100.3300),
        Triple("Parada 5", 25.7000, -100.3350)
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnIniciarRuta = view.findViewById(R.id.btnIniciarRuta)
        tvEstadoRuta = view.findViewById(R.id.tvEstadoRuta)
        tvParadaActual = view.findViewById(R.id.tvParadaActual)
        tvParadasCompletadas = view.findViewById(R.id.tvParadasCompletadas)

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
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }

        paradas.forEach { parada ->
            mMap.addMarker(
                MarkerOptions()
                    .position(LatLng(parada.second, parada.third))
                    .title(parada.first)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
        }

        trazarCaminoPorCalles()

        val primeraParada = LatLng(paradas[0].second, paradas[0].third)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(primeraParada, 15f))
    }

    private fun trazarCaminoPorCalles() {
        val context = GeoApiContext.Builder()
            .apiKey(apiKey)
            .build()

        val origin = "${paradas.first().second},${paradas[0].third}"
        val destination = "${paradas.last().second},${paradas.last().third}"
        val intermediateWaypoints = paradas.subList(1, paradas.size - 1).map { 
            com.google.maps.model.LatLng(it.second, it.third) 
        }.toTypedArray()

        Thread {
            try {
                val result = DirectionsApi.newRequest(context)
                    .mode(TravelMode.DRIVING)
                    .origin(origin)
                    .destination(destination)
                    .waypoints(*intermediateWaypoints)
                    .await()

                if (result.routes.isNotEmpty()) {
                    val path = result.routes[0].overviewPolyline.decodePath()
                    val decodedPath = path.map { LatLng(it.lat, it.lng) }
                    
                    activity?.runOnUiThread {
                        puntosCaminoReal.clear()
                        puntosCaminoReal.addAll(decodedPath)
                        
                        poliLineaRuta?.remove()
                        poliLineaRuta = mMap.addPolyline(PolylineOptions()
                            .addAll(decodedPath)
                            .color(Color.parseColor("#4CAF9E"))
                            .width(12f)
                            .jointType(JointType.ROUND))
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "No se encontraron rutas disponibles", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    // Muestra el error exacto de la API para diagnóstico
                    val errorMsg = e.message ?: "Error desconocido"
                    if (errorMsg.contains("REQUEST_DENIED")) {
                        Toast.makeText(requireContext(), "API Key denegada. Revisa Directions API y SHA-1", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Error API: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun iniciarRuta() {
        if (puntosCaminoReal.isEmpty()) {
            Toast.makeText(requireContext(), "Calculando camino, espera un momento...", Toast.LENGTH_SHORT).show()
            trazarCaminoPorCalles() // Reintento
            return
        }
        
        rutaIniciada = true
        paradasCompletadas = 0

        btnIniciarRuta.text = "Terminar Ruta"
        tvEstadoRuta.text = "Ruta en progreso"
        tvParadaActual.text = "Próxima parada: ${paradas[0].first}"
        tvParadasCompletadas.text = "0/${paradas.size} paradas completadas"

        markerVehiculo = mMap.addMarker(
            MarkerOptions()
                .position(puntosCaminoReal[0])
                .title("Vehículo")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .anchor(0.5f, 0.5f)
                .zIndex(1.0f)
        )

        registrarGeofences()
        animarVehiculoPorCamino(0)

        Toast.makeText(requireContext(), "¡Ruta iniciada!", Toast.LENGTH_SHORT).show()
    }

    private fun animarVehiculoPorCamino(pointIndex: Int) {
        if (!rutaIniciada || pointIndex >= puntosCaminoReal.size - 1) return

        val inicio = puntosCaminoReal[pointIndex]
        val fin = puntosCaminoReal[pointIndex + 1]

        animatorVehiculo = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
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
        paradas.forEachIndexed { index, parada ->
            if (index > paradasCompletadas - 1) {
                val distance = FloatArray(1)
                android.location.Location.distanceBetween(pos.latitude, pos.longitude, parada.second, parada.third, distance)
                if (distance[0] < 50) { 
                    onParadaAlcanzada("parada_$index")
                }
            }
        }
    }

    private fun terminarRuta(guardar: Boolean = false) {
        if (guardar && paradasCompletadas > 0) {
            guardarViajeYActualizarCuenta()
        }

        rutaIniciada = false
        animatorVehiculo?.cancel()
        markerVehiculo?.remove()
        markerVehiculo = null

        btnIniciarRuta.text = "Iniciar Ruta"
        tvEstadoRuta.text = "Ruta terminada"
        tvParadaActual.text = "Próxima parada: --"
        
        geofencingClient.removeGeofences(geofenceIds)
    }

    private fun guardarViajeYActualizarCuenta() {
        val userId = auth.currentUser?.uid ?: return
        val kmRecorridos = (paradasCompletadas * 0.5)
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val nombreViaje = "Viaje - ${sdf.format(Date())}"

        db.collection("usuarios").document(userId).get().addOnSuccessListener { userDoc ->
            val nombre = userDoc.getString("nombre") ?: ""
            val apellidos = userDoc.getString("apellidos") ?: ""
            val nombreCompleto = "$nombre $apellidos".trim()

            val viaje = hashMapOf(
                "userId" to userId,
                "titulo" to nombreViaje,
                "distancia" to "%.1f".format(kmRecorridos),
                "paradas" to paradasCompletadas,
                "fecha" to com.google.firebase.Timestamp.now(),
                "costo" to (kmRecorridos * 15).toInt().toString(),
                "combustible" to "%.2f".format(kmRecorridos * 0.1)
            )
            db.collection("viajes").add(viaje)

            val registroRuta = hashMapOf(
                "chofer" to nombreCompleto,
                "completado" to true,
                "fecha" to com.google.firebase.Timestamp.now(),
                "nombre" to nombreViaje
            )
            db.collection("rutas").add(registroRuta)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Ruta guardada y sincronizada", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun registrarGeofences() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val geofenceList = paradas.mapIndexed { index, parada ->
            Geofence.Builder().setRequestId("parada_$index").setCircularRegion(parada.second, parada.third, 100f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER).build()
        }
        val request = GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(geofenceList).build()
        geofencingClient.addGeofences(request, geofencePendingIntent)
    }

    fun onParadaAlcanzada(paradaId: String) {
        val index = paradaId.replace("parada_", "").toInt()
        if (index < paradasCompletadas) return
        
        paradasCompletadas = index + 1
        tvParadasCompletadas.text = "$paradasCompletadas/${paradas.size} paradas completadas"

        if (index + 1 < paradas.size) {
            tvParadaActual.text = "Próxima parada: ${paradas[index + 1].first}"
        } else {
            tvParadaActual.text = "¡Ruta completada!"
            terminarRuta(true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.isMyLocationEnabled = true
            }
        }
    }

    companion object {
        private val geofenceIds = listOf("parada_0", "parada_1", "parada_2", "parada_3", "parada_4")
    }

    private val geofencePendingIntent by lazy {
        val intent = android.content.Intent(requireContext(), ReceptorGeofence::class.java)
        android.app.PendingIntent.getBroadcast(requireContext(), 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
    }
}