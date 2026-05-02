package org.PIA.geofence.ui.rutas

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
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
import org.PIA.geofence.data.Ruta
import org.PIA.geofence.data.Unidad
import java.time.Instant
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
    private var markersParadas = mutableListOf<Marker>()
    private var poliLineaRapida: Polyline? = null
    private var poliLineaAlterna: Polyline? = null
    private var animatorVehiculo: ValueAnimator? = null
    
    private var puntosRutaRapida = mutableListOf<LatLng>()
    private var puntosRutaAlterna = mutableListOf<LatLng>()
    private var puntosCaminoSeleccionado = mutableListOf<LatLng>()
    private var ultimoIndiceRecorrido = 0
    
    private var kmRutaRapida: Double = 0.0
    private var kmRutaAlterna: Double = 0.0

    private val apiKey = "AIzaSyAjjiNfvcx-3pSKpNULTceVeX46YxLfItc"
    private val PATTERN_ALTERNATE = listOf(Dash(40f), Gap(20f))
    private val MONTERREY = LatLng(25.6866, -100.3161)

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
        
        btnIniciarRuta.setOnClickListener { 
            val textoBoton = btnIniciarRuta.text.toString()
            when (textoBoton) {
                "Terminar ruta" -> finalizarRuta(true)
                "Pausar Recorrido" -> pausarRecorrido()
                else -> iniciarRecorrido()
            }
        }
        
        btnFinalizarRuta.setOnClickListener { 
            if (btnFinalizarRuta.text == "Salir") {
                limpiarPantallaTotalmente()
            } else {
                finalizarRuta(false) // Abandonar
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(MONTERREY, 12f))
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }

        mMap.setOnPolylineClickListener { polyline ->
            if (!rutaIniciada && ultimoIndiceRecorrido == 0) {
                val esRapida = polyline == poliLineaRapida
                puntosCaminoSeleccionado = if (esRapida) puntosRutaRapida else puntosRutaAlterna
                actualizarEstilos()
                Toast.makeText(context, if (esRapida) "Ruta Rápida seleccionada" else "Ruta Alterna seleccionada", Toast.LENGTH_SHORT).show()
            }
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
                    val nuevaRuta = rutaDoc.toObject(Ruta::class.java)?.apply { id = rutaDoc.id }
                    if (nuevaRuta?.id != rutaAsignada?.id || nuevaRuta?.estado != rutaAsignada?.estado) {
                        rutaAsignada = nuevaRuta
                        actualizarUIConRuta(rutaAsignada!!)
                    }
                } else {
                    rutaAsignada = null
                    mostrarSinRuta()
                }
            }
    }

    private fun actualizarUIConRuta(ruta: Ruta) {
        if (!isAdded) return
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
                btnIniciarRuta.text = "Iniciar Recorrido"
                btnFinalizarRuta.visibility = View.VISIBLE
                btnFinalizarRuta.text = "Abandonar"
                tvParadaActual.text = "Toca un camino para elegirlo"
                trazarRutasRealmenteDiferentes(ruta)
            }
            "en_progreso" -> {
                btnAceptarRuta.visibility = View.GONE
                btnIniciarRuta.visibility = View.VISIBLE
                if (rutaIniciada) {
                    btnIniciarRuta.text = "Pausar Recorrido"
                    btnFinalizarRuta.visibility = View.GONE
                } else {
                    btnIniciarRuta.text = "Reanudar Recorrido"
                    btnFinalizarRuta.visibility = View.VISIBLE
                    btnFinalizarRuta.text = "Abandonar"
                }
                tvEstadoRuta.text = "Ruta en Progreso: ${ruta.nombre}"
            }
        }
    }

    private fun mostrarSinRuta() {
        if (!isAdded) return
        tvEstadoRuta.text = "Sin asignar ruta"
        tvParadaActual.text = "Espera a que un despachador te asigne trabajo"
        btnAceptarRuta.visibility = View.GONE
        btnIniciarRuta.visibility = View.GONE
        btnFinalizarRuta.visibility = View.GONE
        if (::mMap.isInitialized) mMap.clear()
        markersParadas.clear()
        puntosCaminoSeleccionado.clear()
        ultimoIndiceRecorrido = 0
    }

    private fun aceptarRuta() {
        val ruta = rutaAsignada ?: return
        val userId = auth.currentUser?.uid ?: return
        db.collection("rutas").document(ruta.id).update("estado", "aceptada").addOnSuccessListener {
            val historialData = hashMapOf(
                "choferId" to userId,
                "nombreChofer" to (ruta.choferNombre ?: ""),
                "despachadorId" to ruta.despachadorId,
                "unidadId" to (ruta.unidadId ?: ""),
                "placaUnidad" to (ruta.unidadPlaca ?: ""),
                "titulo" to ruta.nombre,
                "estado" to "aceptada",
                "fechaInicio" to com.google.firebase.Timestamp.now(),
                "distancia" to "0.0",
                "cantidadParadas" to 0,
                "costo" to "0",
                "combustible" to "0.0"
            )
            db.collection("viajes").document(ruta.id).set(historialData)
            db.collection("usuarios").document(userId).update("estado", "1") // Ocupado
            if (isAdded) Toast.makeText(requireContext(), "Ruta aceptada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun trazarRutasRealmenteDiferentes(ruta: Ruta) {
        if (!::mMap.isInitialized || ruta.paradas.isEmpty()) return
        mMap.clear()
        markersParadas.clear()
        
        val paradasLatLng = ruta.paradas.map { LatLng(it.latitud, it.longitud) }
        paradasLatLng.forEachIndexed { index, pos ->
            val marker = mMap.addMarker(MarkerOptions().position(pos).title(ruta.paradas[index].nombre)
                .icon(BitmapDescriptorFactory.defaultMarker(if (index == 0) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_CYAN)))
            marker?.let { markersParadas.add(it) }
        }

        val geoContext = GeoApiContext.Builder().apiKey(apiKey).build()
        val origin = "${paradasLatLng.first().latitude},${paradasLatLng.first().longitude}"
        val destination = "${paradasLatLng.last().latitude},${paradasLatLng.last().longitude}"
        val waypointsNormal = if (paradasLatLng.size > 2) {
            paradasLatLng.subList(1, paradasLatLng.size - 1).map { "${it.latitude},${it.longitude}" }.toTypedArray()
        } else emptyArray()

        Thread {
            try {
                // 1. RUTA VERDE: Estándar rápida
                val resA = DirectionsApi.newRequest(geoContext).mode(TravelMode.DRIVING)
                    .origin(origin).destination(destination).waypoints(*waypointsNormal)
                    .departureTime(Instant.now()).await()

                if (resA.routes.isNotEmpty()) {
                    val pathA = resA.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                    
                    // 2. RUTA AZUL: Con micro-desvíos precisos por segmento
                    val waypointsForced = mutableListOf<String>()
                    for (i in 0 until paradasLatLng.size - 1) {
                        val p1 = paradasLatLng[i]
                        val p2 = paradasLatLng[i+1]
                        if (i > 0) waypointsForced.add("${p1.latitude},${p1.longitude}")
                        
                        val vLat = p2.latitude - p1.latitude
                        val vLng = p2.longitude - p1.longitude
                        val dist = Math.sqrt(vLat * vLat + vLng * vLng)

                        if (dist > 0.002) { 
                            val offsetScale = 0.0018 // Factor de desvío exacto para calles paralelas
                            val midLat = (p1.latitude + p2.latitude) / 2 + (-vLng / dist * offsetScale)
                            val midLng = (p1.longitude + p2.longitude) / 2 + (vLat / dist * offsetScale)
                            waypointsForced.add("via:$midLat,$midLng")
                        }
                    }

                    val resB = DirectionsApi.newRequest(geoContext).mode(TravelMode.DRIVING).origin(origin).destination(destination)
                        .waypoints(*waypointsForced.toTypedArray())
                        .avoid(DirectionsApi.RouteRestriction.HIGHWAYS).departureTime(Instant.now()).await()

                    activity?.runOnUiThread {
                        puntosRutaRapida = pathA.toMutableList()
                        kmRutaRapida = resA.routes[0].legs.sumOf { it.distance.inMeters.toDouble() } / 1000.0
                        
                        if (resB.routes.isNotEmpty()) {
                            puntosRutaAlterna = resB.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }.toMutableList()
                            kmRutaAlterna = resB.routes[0].legs.sumOf { it.distance.inMeters.toDouble() } / 1000.0
                        } else puntosRutaAlterna = puntosRutaRapida.toMutableList()
                        
                        puntosCaminoSeleccionado = puntosRutaRapida
                        dibujarLineas()
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pathA[0], 14f))
                    }
                }
            } catch (e: Exception) { Log.e("Rutas", "Error: ${e.message}") }
        }.start()
    }

    private fun dibujarLineas() {
        poliLineaRapida?.remove()
        poliLineaAlterna?.remove()
        poliLineaRapida = mMap.addPolyline(PolylineOptions().addAll(puntosRutaRapida).clickable(true))
        poliLineaAlterna = mMap.addPolyline(PolylineOptions().addAll(puntosRutaAlterna).clickable(true))
        actualizarEstilos()
    }

    private fun actualizarEstilos() {
        val colorVerde = Color.parseColor("#4CAF50")
        val colorAzul = Color.parseColor("#2196F3")
        
        val esRutaRapidaSeleccionada = puntosCaminoSeleccionado.size == puntosRutaRapida.size && 
                                      (puntosCaminoSeleccionado.firstOrNull() == puntosRutaRapida.firstOrNull())

        if (esRutaRapidaSeleccionada) {
            poliLineaRapida?.apply { color = colorVerde; width = 8f; zIndex = 10f; pattern = null }
            poliLineaAlterna?.apply { color = ColorUtils.setAlphaComponent(colorAzul, 200); width = 16f; zIndex = 5f; pattern = PATTERN_ALTERNATE }
        } else {
            poliLineaAlterna?.apply { color = colorAzul; width = 8f; zIndex = 10f; pattern = null }
            poliLineaRapida?.apply { color = ColorUtils.setAlphaComponent(colorVerde, 200); width = 16f; zIndex = 5f; pattern = PATTERN_ALTERNATE }
        }
    }

    private fun iniciarRecorrido() {
        if (puntosCaminoSeleccionado.isEmpty()) return
        rutaIniciada = true
        btnIniciarRuta.text = "Pausar Recorrido"
        tvEstadoRuta.text = "En progreso"
        btnFinalizarRuta.visibility = View.GONE
        
        db.collection("rutas").document(rutaAsignada!!.id).update("estado", "en_progreso")
        db.collection("viajes").document(rutaAsignada!!.id).update("estado", "en progreso")
        
        // Cambiar estado de la unidad a "En ruta"
        rutaAsignada?.unidadId?.let { uid ->
            db.collection("unidades").document(uid).update("estado", "En ruta")
        }
        
        if (markerVehiculo == null) {
            markerVehiculo = mMap.addMarker(MarkerOptions().position(puntosCaminoSeleccionado[ultimoIndiceRecorrido]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).anchor(0.5f, 0.5f).zIndex(20f))
        }
        animarVehiculo(ultimoIndiceRecorrido)
    }

    private fun pausarRecorrido() {
        rutaIniciada = false
        animatorVehiculo?.cancel()
        btnIniciarRuta.text = "Reanudar Recorrido"
        tvEstadoRuta.text = "Ruta pausada"
        btnFinalizarRuta.visibility = View.VISIBLE
        btnFinalizarRuta.text = "Abandonar"
    }

    private fun animarVehiculo(index: Int) {
        if (!rutaIniciada || index >= puntosCaminoSeleccionado.size - 1) return
        ultimoIndiceRecorrido = index
        val inicio = puntosCaminoSeleccionado[index]
        val fin = puntosCaminoSeleccionado[index + 1]
        animatorVehiculo = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val f = animation.animatedValue as Float
                val pos = LatLng((fin.latitude-inicio.latitude)*f+inicio.latitude, (fin.longitude-inicio.longitude)*f+inicio.longitude)
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
        val paradas = rutaAsignada?.paradas ?: return
        if (paradasCompletadas < paradas.size) {
            val p = paradas[paradasCompletadas]
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(pos.latitude, pos.longitude, p.latitud, p.longitud, dist)
            
            // Radio de detección: 40 metros para el destino final, 75 para intermedias
            val radio = if (paradasCompletadas == paradas.size - 1) 40 else 75
            
            if (dist[0] < radio) {
                paradasCompletadas++
                tvParadasCompletadas.visibility = View.VISIBLE
                tvParadasCompletadas.text = "$paradasCompletadas/${paradas.size} completadas"
                
                if (paradasCompletadas >= paradas.size) {
                    rutaIniciada = false
                    animatorVehiculo?.cancel()
                    btnIniciarRuta.text = "Terminar ruta"
                    btnIniciarRuta.isEnabled = true
                    tvEstadoRuta.text = "¡Destino alcanzado!"
                    btnFinalizarRuta.visibility = View.GONE
                } else {
                    tvParadaActual.text = "Próxima: ${paradas[paradasCompletadas].nombre}"
                }
            }
        }
    }

    private fun finalizarRuta(guardar: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val rutaId = rutaAsignada?.id ?: return
        rutaIniciada = false
        animatorVehiculo?.cancel()
        
        val currentPos = markerVehiculo?.position
        poliLineaRapida?.remove()
        poliLineaAlterna?.remove()
        markerVehiculo?.remove()
        markerVehiculo = null

        if (currentPos != null && puntosCaminoSeleccionado.isNotEmpty()) {
            val traveled = puntosCaminoSeleccionado.subList(0, ultimoIndiceRecorrido + 1).toMutableList()
            traveled.add(currentPos)
            val remaining = mutableListOf(currentPos)
            remaining.addAll(puntosCaminoSeleccionado.subList(ultimoIndiceRecorrido + 1, puntosCaminoSeleccionado.size))
            val color = if (puntosCaminoSeleccionado.size == puntosRutaRapida.size) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3")
            if (traveled.size >= 2) mMap.addPolyline(PolylineOptions().addAll(traveled).color(color).width(8f).zIndex(10f))
            if (remaining.size >= 2) mMap.addPolyline(PolylineOptions().addAll(remaining).color(Color.LTGRAY).width(8f).zIndex(5f))
        }

        markersParadas.forEachIndexed { i, m -> if (i >= paradasCompletadas) m.alpha = 0.4f }
        
        if (guardar && rutaAsignada != null) {
            actualizarHistorialFinal(rutaId)
            db.collection("usuarios").document(userId).update("estado", "0")
            
            // Cambiar estado de la unidad a "Disponible"
            rutaAsignada?.unidadId?.let { uid ->
                db.collection("unidades").document(uid).update("estado", "Disponible")
            }
            
            tvEstadoRuta.text = "Viaje Finalizado"
        } else {
            tvEstadoRuta.text = "Ruta Interrumpida"
            db.collection("rutas").document(rutaId).update("estado", "cancelada")
            db.collection("usuarios").document(userId).update("estado", "0")
            
            // Cambiar estado de la unidad a "Disponible" incluso si se interrumpe
            rutaAsignada?.unidadId?.let { uid ->
                db.collection("unidades").document(uid).update("estado", "Disponible")
            }
        }
        btnIniciarRuta.isEnabled = false
        btnFinalizarRuta.visibility = View.VISIBLE
        btnFinalizarRuta.text = "Salir"
    }

    private fun actualizarHistorialFinal(rutaId: String) {
        val km = if (puntosCaminoSeleccionado.size == puntosRutaRapida.size) kmRutaRapida else kmRutaAlterna
        val updates = hashMapOf(
            "estado" to "completada",
            "distancia" to "%.1f km".format(km),
            "cantidadParadas" to paradasCompletadas,
            "costo" to (km * 15).toInt().toString(),
            "combustible" to "%.2f".format(km * 0.12),
            "fechaFin" to com.google.firebase.Timestamp.now()
        )
        db.collection("rutas").document(rutaId).update("estado", "completada", "completado", true)
        db.collection("viajes").document(rutaId).update(updates as Map<String, Any>)
    }

    private fun actualizarEstadoDisponible() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(userId).update("estado", "0")
        
        // También liberar la unidad si hay una activa
        rutaAsignada?.unidadId?.let { uid ->
            db.collection("unidades").document(uid).update("estado", "Disponible")
        }
    }

    private fun limpiarPantallaTotalmente() {
        mMap.clear()
        markersParadas.clear()
        ultimoIndiceRecorrido = 0
        paradasCompletadas = 0
        puntosCaminoSeleccionado.clear()
        rutaIniciada = false
        animatorVehiculo?.cancel()
        markerVehiculo = null
        actualizarEstadoDisponible()
        mostrarSinRuta()
    }

    override fun onDestroyView() { super.onDestroyView() ; rutaListener?.remove() }
}
