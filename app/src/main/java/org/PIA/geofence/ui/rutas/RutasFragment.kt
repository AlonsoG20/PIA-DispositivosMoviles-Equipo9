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
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }

        paradas.forEach { parada ->
            mMap.addMarker(
                MarkerOptions()
                    .position(LatLng(parada.second, parada.third))
                    .title(parada.first)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
        }

        val polylineOptions = PolylineOptions()
            .addAll(paradas.map { LatLng(it.second, it.third) })
            .color(Color.parseColor("#4CAF9E"))
            .width(12f)
            .jointType(JointType.ROUND)
        poliLineaRuta = mMap.addPolyline(polylineOptions)

        val primeraParada = LatLng(paradas[0].second, paradas[0].third)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(primeraParada, 15f))
    }

    private fun iniciarRuta() {
        rutaIniciada = true
        paradasCompletadas = 0

        btnIniciarRuta.text = "Terminar Ruta"
        tvEstadoRuta.text = "Ruta en progreso"
        tvParadaActual.text = "Próxima parada: ${paradas[0].first}"
        tvParadasCompletadas.text = "0/${paradas.size} paradas completadas"

        val inicio = LatLng(paradas[0].second, paradas[0].third)
        markerVehiculo = mMap.addMarker(
            MarkerOptions()
                .position(inicio)
                .title("Vehículo")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .anchor(0.5f, 0.5f)
                .zIndex(1.0f)
        )

        registrarGeofences()
        animarVehiculoEntrePuntos(0)

        Toast.makeText(requireContext(), "¡Ruta iniciada!", Toast.LENGTH_SHORT).show()
    }

    private fun animarVehiculoEntrePuntos(index: Int) {
        if (!rutaIniciada || index >= paradas.size - 1) return

        val inicio = LatLng(paradas[index].second, paradas[index].third)
        val fin = LatLng(paradas[index + 1].second, paradas[index + 1].third)

        animatorVehiculo = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5000
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val lat = (fin.latitude - inicio.latitude) * fraction + inicio.latitude
                val lng = (fin.longitude - inicio.longitude) * fraction + inicio.longitude
                val currentPos = LatLng(lat, lng)
                
                markerVehiculo?.position = currentPos
                mMap.animateCamera(CameraUpdateFactory.newLatLng(currentPos))
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (rutaIniciada) {
                        onParadaAlcanzada("parada_${index + 1}")
                        animarVehiculoEntrePuntos(index + 1)
                    }
                }
            })
            start()
        }
    }

    private fun terminarRuta(guardar: Boolean = false) {
        if (guardar && paradasCompletadas > 0) {
            guardarViajeEnHistorial()
        }

        rutaIniciada = false
        animatorVehiculo?.cancel()
        markerVehiculo?.remove()
        markerVehiculo = null

        btnIniciarRuta.text = "Iniciar Ruta"
        tvEstadoRuta.text = "Ruta terminada"
        tvParadaActual.text = "Próxima parada: --"
        tvParadasCompletadas.text = "$paradasCompletadas/${paradas.size} paradas completadas"

        geofencingClient.removeGeofences(geofenceIds)
        Toast.makeText(requireContext(), "Ruta terminada", Toast.LENGTH_SHORT).show()
    }

    private fun guardarViajeEnHistorial() {
        val userId = auth.currentUser?.uid ?: return
        val kmRecorridos = (paradasCompletadas * 0.5) 

        // Uso de SimpleDateFormat (común en Kotlin) para un nombre legible
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val nombreViaje = "Viaje - ${sdf.format(Date())}"

        val viaje = hashMapOf(
            "userId" to userId,
            "titulo" to nombreViaje,
            "distancia" to "%.1f".format(kmRecorridos),
            "paradas" to paradasCompletadas,
            "fecha" to com.google.firebase.Timestamp.now(),
            "costo" to (kmRecorridos * 15).toInt().toString(),
            "combustible" to "%.2f".format(kmRecorridos * 0.1)
        )

        db.collection("viajes")
            .add(viaje)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Viaje guardado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun registrarGeofences() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val geofenceList = paradas.mapIndexed { index, parada ->
            Geofence.Builder()
                .setRequestId("parada_$index")
                .setCircularRegion(parada.second, parada.third, 100f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofenceList)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent)
    }

    fun onParadaAlcanzada(paradaId: String) {
        val index = paradaId.replace("parada_", "").toInt()
        paradasCompletadas = index + 1

        tvParadasCompletadas.text = "$paradasCompletadas/${paradas.size} paradas completadas"

        if (index + 1 < paradas.size) {
            tvParadaActual.text = "Próxima parada: ${paradas[index + 1].first}"
        } else {
            tvParadaActual.text = "¡Ruta completada!"
            terminarRuta(true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.isMyLocationEnabled = true
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private val geofenceIds = listOf(
            "parada_0", "parada_1", "parada_2", "parada_3", "parada_4"
        )
    }

    private val geofencePendingIntent by lazy {
        val intent = android.content.Intent(requireContext(), ReceptorGeofence::class.java)
        android.app.PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
    }
}