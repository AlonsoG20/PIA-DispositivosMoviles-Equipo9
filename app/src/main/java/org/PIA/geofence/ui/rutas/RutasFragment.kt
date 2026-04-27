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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.PIA.geofence.BuildConfig
import org.PIA.geofence.R
import org.PIA.geofence.data.Viaje
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

    private var rutaAsignada: Viaje? = null
    private var rutaListener: ListenerRegistration? = null
    
    private var rutaIniciada = false
    private var paradasCompletadas = 0
    private var markerVehiculo: Marker? = null
    private var poliLineaRuta: Polyline? = null
    private var animatorVehiculo: ValueAnimator? = null
    private var puntosCaminoReal = mutableListOf<LatLng>()

    private val apiKey = BuildConfig.MAPS_API_KEY

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

        rutaListener = db.collection("viajes")
            .whereEqualTo("choferId", userId)
            .whereIn("estado", listOf("pendiente", "aceptada", "en_progreso"))
            .addSnapshotListener { snapshot, _ ->
                val rutaDoc = snapshot?.documents?.firstOrNull()
                if (rutaDoc != null) {
                    rutaAsignada = rutaDoc.toObject(Viaje::class.java)
                    actualizarUIConRuta(rutaAsignada!!)
                } else {
                    rutaAsignada = null
                    mostrarSinRuta()
                }
            }
    }

    private fun actualizarUIConRuta(ruta: Viaje) {
        if (!isAdded) return
        tvEstadoRuta.text = "Ruta Asignada: ${ruta.titulo}"
        
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
                tvEstadoRuta.text = "Ruta en Progreso: ${ruta.titulo}"
            }
        }
    }

    private fun mostrarSinRuta() {
        if (!isAdded) return
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
        db.collection("viajes").document(rutaId)
            .update("estado", "aceptada")
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Ruta aceptada", Toast.LENGTH_SHORT).show()
            }
    }

    private fun trazarRutaEnMapa(ruta: Viaje) {
        if (!::mMap.isInitialized || ruta.puntosParada.isEmpty()) return
        
        mMap.clear()
        ruta.puntosParada.forEachIndexed { index, point ->
            mMap.addMarker(MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .title("Parada ${index + 1}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
        }

        val context = GeoApiContext.Builder().apiKey(apiKey).build()
        val origin = "${ruta.puntosParada.first().latitude},${ruta.puntosParada.first().longitude}"
        val destination = "${ruta.puntosParada.last().latitude},${ruta.puntosParada.last().longitude}"
        val intermediate = if (ruta.puntosParada.size > 2) {
            ruta.puntosParada.subList(1, ruta.puntosParada.size - 1).map {
                com.google.maps.model.LatLng(it.latitude, it.longitude)
            }.toTypedArray()
        } else emptyArray()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
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
                    
                    withContext(Dispatchers.Main) {
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
                withContext(Dispatchers.Main) {
                    if (isAdded) Toast.makeText(requireContext(), "Error al trazar calles: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun iniciarRecorrido() {
        if (puntosCaminoReal.isEmpty()) return
        
        val rutaId = rutaAsignada?.id ?: return
        rutaIniciada = true
        paradasCompletadas = 0
        db.collection("viajes").document(rutaId).update("estado", "en_progreso")

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
        rutaAsignada?.puntosParada?.forEachIndexed { index, point ->
            if (index > paradasCompletadas - 1) {
                val distance = FloatArray(1)
                android.location.Location.distanceBetween(pos.latitude, pos.longitude, point.latitude, point.longitude, distance)
                if (distance[0] < 50) { 
                    onParadaAlcanzada(index)
                }
            }
        }
    }

    private fun onParadaAlcanzada(index: Int) {
        if (index < paradasCompletadas) return
        
        paradasCompletadas = index + 1
        tvParadasCompletadas.visibility = View.VISIBLE
        tvParadasCompletadas.text = "$paradasCompletadas/${rutaAsignada?.puntosParada?.size ?: 0} paradas completadas"

        if (index + 1 < (rutaAsignada?.puntosParada?.size ?: 0)) {
            tvParadaActual.text = "Próxima parada: Parada ${index + 2}"
        }
    }

    private fun finalizarRuta(guardar: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val rutaId = rutaAsignada?.id ?: return
        val unidadId = rutaAsignada?.unidadId

        rutaIniciada = false
        
        // 1. Marcar ruta como completada
        db.collection("viajes").document(rutaId).update(
            "estado", "completada",
            "fechaFin", com.google.firebase.Timestamp.now()
        )
        
        // 2. Liberar al Chofer (estado = "0")
        db.collection("usuarios").document(userId).update("estado", "0")
        
        // 3. Liberar la Unidad (estado = "Disponible") si existe
        if (!unidadId.isNullOrEmpty()) {
            db.collection("unidades").document(unidadId).update("estado", "Disponible")
        }

        animatorVehiculo?.cancel()
        markerVehiculo?.remove()
        
        if (isAdded) Toast.makeText(requireContext(), "Ruta finalizada. Ahora estás disponible.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rutaListener?.remove()
    }
}
