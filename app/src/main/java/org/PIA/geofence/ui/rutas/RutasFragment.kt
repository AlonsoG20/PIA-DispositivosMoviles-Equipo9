package org.PIA.geofence.ui.rutas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
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
import org.PIA.geofence.R
import org.PIA.geofence.ReceptorGeofence

class RutasFragment : Fragment(R.layout.fragment_rutas), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient

    private lateinit var btnIniciarRuta: Button
    private lateinit var tvEstadoRuta: TextView
    private lateinit var tvParadaActual: TextView
    private lateinit var tvParadasCompletadas: TextView

    private var rutaIniciada = false
    private var paradasCompletadas = 0

    // Paradas fijas de la ruta (nombre, latitud, longitud)
    private val paradas = listOf(
        Triple("Parada 1", 25.6866, -100.3161),
        Triple("Parada 2", 25.6900, -100.3200),
        Triple("Parada 3", 25.6950, -100.3250),
        Triple("Parada 4", 25.6980, -100.3300),
        Triple("Parada 5", 25.7000, -100.3350)
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Conectamos las vistas
        btnIniciarRuta = view.findViewById(R.id.btnIniciarRuta)
        tvEstadoRuta = view.findViewById(R.id.tvEstadoRuta)
        tvParadaActual = view.findViewById(R.id.tvParadaActual)
        tvParadasCompletadas = view.findViewById(R.id.tvParadasCompletadas)

        // Inicializamos los clientes de ubicación y geofencing
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        geofencingClient = LocationServices.getGeofencingClient(requireActivity())

        // Inicializamos el mapa
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Botón iniciar/terminar ruta
        btnIniciarRuta.setOnClickListener {
            if (!rutaIniciada) {
                iniciarRuta()
            } else {
                terminarRuta()
            }
        }
    }

    // Se llama cuando el mapa está listo
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Activamos el botón de mi ubicación si tenemos permiso
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            // Pedimos el permiso si no lo tenemos
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }

        // Ponemos los marcadores de las paradas en el mapa
        paradas.forEach { parada ->
            mMap.addMarker(
                MarkerOptions()
                    .position(LatLng(parada.second, parada.third))
                    .title(parada.first)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
        }

        // Centramos la cámara en la primera parada
        val primeraParada = LatLng(paradas[0].second, paradas[0].third)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(primeraParada, 14f))
    }

    private fun iniciarRuta() {
        rutaIniciada = true
        paradasCompletadas = 0

        // Actualizamos la UI
        btnIniciarRuta.text = "Terminar Ruta"
        btnIniciarRuta.backgroundTintList = null
        btnIniciarRuta.setBackgroundResource(R.drawable.button_background)
        tvEstadoRuta.text = "Ruta en progreso"
        tvParadaActual.text = "Próxima parada: ${paradas[0].first}"
        tvParadasCompletadas.text = "0/${paradas.size} paradas completadas"

        // Registramos los geofences de cada parada
        registrarGeofences()
        // Dibujamos un círculo en cada parada para visualizar el geofence
        paradas.forEach { parada ->
            mMap.addCircle(
                CircleOptions()
                    .center(LatLng(parada.second, parada.third))
                    .radius(100.0) // mismos 100 metros del geofence
                    .strokeColor(0xFF4CAF9E.toInt())
                    .fillColor(0x334CAF9E)
                    .strokeWidth(3f)
            )
        }

        Toast.makeText(requireContext(), "¡Ruta iniciada!", Toast.LENGTH_SHORT).show()
    }

    private fun terminarRuta() {
        rutaIniciada = false

        // Actualizamos la UI
        btnIniciarRuta.text = "Iniciar Ruta"
        tvEstadoRuta.text = "Ruta terminada"
        tvParadaActual.text = "Próxima parada: --"
        tvParadasCompletadas.text = "$paradasCompletadas/${paradas.size} paradas completadas"

        // Eliminamos los geofences
        geofencingClient.removeGeofences(geofenceIds)

        Toast.makeText(requireContext(), "Ruta terminada", Toast.LENGTH_SHORT).show()
    }

    private fun registrarGeofences() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Creamos un geofence por cada parada con radio de 100 metros
        val geofenceList = paradas.mapIndexed { index, parada ->
            Geofence.Builder()
                .setRequestId("parada_$index")
                .setCircularRegion(parada.second, parada.third, 100f) // 100 metros de radio
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofenceList)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Geofences activados", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al activar geofences: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Cuando el chofer entra a una parada
    fun onParadaAlcanzada(paradaId: String) {
        val index = paradaId.replace("parada_", "").toInt()
        paradasCompletadas++

        tvParadasCompletadas.text = "$paradasCompletadas/${paradas.size} paradas completadas"

        // Actualizamos la próxima parada
        if (index + 1 < paradas.size) {
            tvParadaActual.text = "Próxima parada: ${paradas[index + 1].first}"
        } else {
            tvParadaActual.text = "Última parada alcanzada"
        }

        Toast.makeText(requireContext(),
            "¡Llegaste a ${paradas[index].first}!", Toast.LENGTH_SHORT).show()
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
            } else {
                Toast.makeText(requireContext(),
                    "Se necesita permiso de ubicación", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private val geofenceIds = listOf(
            "parada_0", "parada_1", "parada_2", "parada_3", "parada_4"
        )
    }

    // PendingIntent para recibir eventos de geofencing
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