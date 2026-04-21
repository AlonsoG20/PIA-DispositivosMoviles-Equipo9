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
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import org.PIA.geofence.R
import org.PIA.geofence.ReceptorGeofence
import org.PIA.geofence.data.Ruta
import java.text.SimpleDateFormat
import java.util.*

class RutasFragment : Fragment(R.layout.fragment_rutas), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvEstadoRuta: TextView
    private lateinit var tvParadaActual: TextView
    private lateinit var tvParadasCompletadas: TextView
    private lateinit var btnAceptarRuta: Button
    private lateinit var btnIniciarRuta: Button
    private lateinit var btnFinalizarRuta: Button

    private var rutaAsignada: Ruta? = null
    private var rutaListener: ListenerRegistration? = null
    
    private var rutaIniciada = false
    private var paradasCompletadas = 0
    private var markerVehiculo: Marker? = null
    private var poliLineaRuta: Polyline? = null
    private var animatorVehiculo: ValueAnimator? = null
    private var puntosCaminoReal = mutableListOf<LatLng>()

    private val apiKey = "AIzaSyAjjiNfvcx-3pSKpNULTceVeX46YxLfItc"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvEstadoRuta = view.findViewById(R.id.tvEstadoRuta)
        tvParadaActual = view.findViewById(R.id.tvParadaActual)
        tvParadasCompletadas = view.findViewById(R.id.tvParadasCompletadas)
        btnAceptarRuta = view.findViewById(R.id.btnAceptarRuta)
        btnIniciarRuta = view.findViewById(R.id.btnIniciarRuta)
        btnFinalizarRuta = view.findViewById(R.id.btnAbandonarRuta)

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        escucharRutasAsignadas()

        btnAceptarRuta.setOnClickListener { aceptarRuta() }
        btnIniciarRuta.setOnClickListener { iniciarRecorrido() }
        btnFinalizarRuta.setOnClickListener { finalizarRuta(true) }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
        
        rutaAsignada?.let {
            if (it.estado == "aceptada") trazarRutaEnMapa(it)
        }
    }

    private fun escucharRutasAsignadas() {
        val userId = auth.currentUser?.uid ?: return

        rutaListener = db.collection("rutas")
            .whereEqualTo("choferId", userId)
            .whereIn("estado", listOf("pendiente", "aceptada", "en_progreso"))
            .addSnapshotListener { snapshot, _ ->
                val rutaDoc = snapshot?.documents?.firstOrNull()
                if (rutaDoc != null) {
                    rutaAsignada = rutaDoc.toObject(Ruta::class.java)
                    actualizarUIConRuta(rutaAsignada!!)
                } else {
                    rutaAsignada = null
                    mostrarSinRuta()
                }
            }
    }

    private fun actualizarUIConRuta(ruta: Ruta) {
        tvEstadoRuta.text = "Ruta Asignada: ${ruta.nombre}"
        
        when (ruta.estado) {
            "pendiente" -> {
                tvParadaActual.text = "Tienes una ruta pendiente. ¿Deseas aceptarla?"
                btnAceptarRuta.visibility = View.VISIBLE
                btnIniciarRuta.visibility = View.GONE
                btnFinalizarRuta.visibility = View.GONE
            }
            "aceptada" -> {
                btnAceptarRuta.visibility = View.GONE
                btnIniciarRuta.visibility = View.VISIBLE
                btnFinalizarRuta.visibility = View.VISIBLE
                tvParadaActual.text = "Ruta lista para iniciar"
                trazarRutaEnMapa(ruta)
            }
            "en_progreso" -> {
                btnAceptarRuta.visibility = View.GONE
                btnIniciarRuta.visibility = View.GONE
                btnFinalizarRuta.visibility = View.VISIBLE
                tvEstadoRuta.text = "Ruta en Progreso: ${ruta.nombre}"
            }
        }
    }

    private fun mostrarSinRuta() {
        tvEstadoRuta.text = "Sin rutas asignadas"
        tvParadaActual.text = "Espera a que un despachador te asigne trabajo"
        btnAceptarRuta.visibility = View.GONE
        btnIniciarRuta.visibility = View.GONE
        btnFinalizarRuta.visibility = View.GONE
        
        if (::mMap.isInitialized) {
            mMap.clear()
        }
        puntosCaminoReal.clear()
    }

    private fun aceptarRuta() {
        val rutaId = rutaAsignada?.id ?: return
        db.collection("rutas").document(rutaId)
            .update("estado", "aceptada")
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Ruta aceptada", Toast.LENGTH_SHORT).show()
            }
    }

    private fun trazarRutaEnMapa(ruta: Ruta) {
        if (!::mMap.isInitialized || ruta.paradas.isEmpty()) return
        
        mMap.clear()
        ruta.paradas.forEach { parada ->
            mMap.addMarker(MarkerOptions()
                .position(LatLng(parada.latitud, parada.longitud))
                .title(parada.nombre)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
        }

        val context = GeoApiContext.Builder().apiKey(apiKey).build()
        val origin = "${ruta.paradas.first().latitud},${ruta.paradas.first().longitud}"
        val destination = "${ruta.paradas.last().latitud},${ruta.paradas.last().longitud}"
        val intermediate = ruta.paradas.subList(1, ruta.paradas.size - 1).map {
            com.google.maps.model.LatLng(it.latitud, it.longitud)
        }.toTypedArray()

        Thread {
            try {
                val result = DirectionsApi.newRequest(context)
                    .mode(TravelMode.DRIVING)
                    .origin(origin)
                    .destination(destination)
                    .waypoints(*intermediate)
                    .await()

                if (result.routes.isNotEmpty()) {
                    val path = result.routes[0].overviewPolyline.decodePath()
                    val decodedPath = path.map { LatLng(it.lat, it.lng) }
                    
                    activity?.runOnUiThread {
                        puntosCaminoReal.clear()
                        puntosCaminoReal.addAll(decodedPath)
                        poliLineaRuta = mMap.addPolyline(PolylineOptions()
                            .addAll(decodedPath)
                            .color(Color.parseColor("#4CAF9E"))
                            .width(12f))
                        
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(decodedPath[0], 14f))
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error al trazar calles: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun iniciarRecorrido() {
        if (puntosCaminoReal.isEmpty()) return
        
        val rutaId = rutaAsignada?.id ?: return
        rutaIniciada = true
        paradasCompletadas = 0
        db.collection("rutas").document(rutaId).update("estado", "en_progreso")

        markerVehiculo = mMap.addMarker(MarkerOptions()
            .position(puntosCaminoReal[0])
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            .anchor(0.5f, 0.5f))

        animarVehiculo(0)
    }

    private fun animarVehiculo(index: Int) {
        if (!rutaIniciada || index >= puntosCaminoReal.size - 1) {
            finalizarRuta(true)
            return
        }

        val inicio = puntosCaminoReal[index]
        val fin = puntosCaminoReal[index + 1]

        animatorVehiculo = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val f = animation.animatedValue as Float
                val pos = LatLng(
                    (fin.latitude - inicio.latitude) * f + inicio.latitude,
                    (fin.longitude - inicio.longitude) * f + inicio.longitude
                )
                markerVehiculo?.position = pos
                mMap.moveCamera(CameraUpdateFactory.newLatLng(pos))
                verificarLlegadaAParada(pos)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (rutaIniciada) animarVehiculo(index + 1)
                }
            })
            start()
        }
    }

    private fun verificarLlegadaAParada(pos: LatLng) {
        rutaAsignada?.paradas?.forEachIndexed { index, parada ->
            if (index > paradasCompletadas - 1) {
                val distance = FloatArray(1)
                android.location.Location.distanceBetween(pos.latitude, pos.longitude, parada.latitud, parada.longitud, distance)
                if (distance[0] < 50) { 
                    onParadaAlcanzada("parada_$index")
                }
            }
        }
    }

    private fun onParadaAlcanzada(paradaId: String) {
        val index = paradaId.replace("parada_", "").toInt()
        if (index < paradasCompletadas) return
        
        paradasCompletadas = index + 1
        tvParadasCompletadas.visibility = View.VISIBLE
        tvParadasCompletadas.text = "$paradasCompletadas/${rutaAsignada?.paradas?.size ?: 0} paradas completadas"

        if (index + 1 < (rutaAsignada?.paradas?.size ?: 0)) {
            tvParadaActual.text = "Próxima parada: ${rutaAsignada?.paradas?.get(index + 1)?.nombre}"
        }
    }

    private fun finalizarRuta(guardar: Boolean) {
        rutaIniciada = false
        val rutaId = rutaAsignada?.id ?: return
        db.collection("rutas").document(rutaId).update("estado", "completada")
        
        if (guardar) {
            guardarViajeYActualizarCuenta()
        }

        animatorVehiculo?.cancel()
        markerVehiculo?.remove()
        
        Toast.makeText(requireContext(), "Ruta completada", Toast.LENGTH_SHORT).show()
    }

    private fun guardarViajeYActualizarCuenta() {
        val userId = auth.currentUser?.uid ?: return
        val totalParadas = rutaAsignada?.paradas?.size ?: 1
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
                "distancia" to String.format(Locale.US, "%.1f", kmRecorridos),
                "paradas" to paradasCompletadas,
                "fecha" to com.google.firebase.Timestamp.now(),
                "costo" to (kmRecorridos * 15).toInt().toString(),
                "combustible" to String.format(Locale.US, "%.2f", kmRecorridos * 0.1)
            )
            db.collection("viajes").add(viaje)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rutaListener?.remove()
    }
}