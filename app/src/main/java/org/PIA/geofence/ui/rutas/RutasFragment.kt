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
import org.PIA.geofence.data.Viaje
import java.time.Instant
import java.util.*

class RutasFragment : Fragment(R.layout.fragment_rutas), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var btnIniciarRuta: Button
    private lateinit var btnAbandonarRuta: Button
    private lateinit var tvEstadoRuta: TextView
    private lateinit var tvParadaActual: TextView
    private lateinit var tvParadasCompletadas: TextView

    private var rutaIniciada = false
    private var paradasCompletadas = 0
    private var viajeActivo: Viaje? = null
    private var viajeListener: ListenerRegistration? = null

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
    private val MONTERREY = LatLng(25.6866, -100.3161)

    private val PATTERN_ALTERNATE = listOf(Dash(40f), Gap(20f))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnIniciarRuta = view.findViewById(R.id.btnIniciarRuta)
        btnAbandonarRuta = view.findViewById(R.id.btnAbandonarRuta)
        tvEstadoRuta = view.findViewById(R.id.tvEstadoRuta)
        tvParadaActual = view.findViewById(R.id.tvParadaActual)
        tvParadasCompletadas = view.findViewById(R.id.tvParadasCompletadas)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnIniciarRuta.setOnClickListener {
            when (btnIniciarRuta.text.toString()) {
                "Terminar ruta" -> terminarRuta(true)
                "Pausar Ruta" -> pausarRuta()
                else -> iniciarRuta()
            }
        }

        btnAbandonarRuta.setOnClickListener {
            val status = tvEstadoRuta.text.toString()
            if (status == "Viaje Finalizado" || status == "Ruta Interrumpida" || status == "Sin asignar ruta") {
                limpiarPantallaTotalmente()
            } else {
                terminarRuta(true)
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
                puntosCaminoSeleccionado = if (polyline == poliLineaRapida) puntosRutaRapida else puntosRutaAlterna
                actualizarEstilos()
                Toast.makeText(context, "Trayecto cambiado", Toast.LENGTH_SHORT).show()
            }
        }

        escucharViajeAsignado()
    }

    private fun escucharViajeAsignado() {
        val userId = auth.currentUser?.uid ?: return
        viajeListener = db.collection("viajes")
            .whereEqualTo("userId", userId)
            .whereEqualTo("fechaFin", null)
            .addSnapshotListener { snapshot, _ ->
                val viaje = snapshot?.toObjects(Viaje::class.java)?.firstOrNull()
                if (viaje != null && viajeActivo?.id != viaje.id && !rutaIniciada) {
                    viajeActivo = viaje
                    prepararMapa(viaje)
                }
            }
    }

    private fun prepararMapa(viaje: Viaje) {
        mMap.clear()
        markersParadas.clear()
        ultimoIndiceRecorrido = 0
        paradasCompletadas = 0
        
        val paradas = viaje.puntosParada.map { LatLng(it.latitude, it.longitude) }
        if (paradas.isEmpty()) return

        paradas.forEachIndexed { index, pos ->
            val title = if (index == 0) "Inicio" else if (index == paradas.size - 1) "Destino" else "Parada ${index + 1}"
            val marker = mMap.addMarker(MarkerOptions().position(pos).title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(if (index == 0) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_CYAN)))
            marker?.let { markersParadas.add(it) }
        }

        trazarRutasRealmenteDiferentes(paradas)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(paradas[0], 13f))
        btnIniciarRuta.isEnabled = true
        btnAbandonarRuta.visibility = View.VISIBLE
        btnAbandonarRuta.text = "Abandonar"
        tvEstadoRuta.text = "Viaje asignado: ${viaje.titulo}"
        tvParadaActual.text = "Toca un camino para elegirlo"
    }

    private fun trazarRutasRealmenteDiferentes(paradas: List<LatLng>) {
        val geoContext = GeoApiContext.Builder().apiKey(apiKey).build()
        val origin = "${paradas.first().latitude},${paradas.first().longitude}"
        val destination = "${paradas.last().latitude},${paradas.last().longitude}"
        
        val waypointsNormal = if (paradas.size > 2) {
            paradas.subList(1, paradas.size - 1).map { "${it.latitude},${it.longitude}" }.toTypedArray()
        } else emptyArray()

        Thread {
            try {
                val resA = DirectionsApi.newRequest(geoContext)
                    .mode(TravelMode.DRIVING).origin(origin).destination(destination)
                    .waypoints(*waypointsNormal).departureTime(Instant.now()).await()

                if (resA.routes.isNotEmpty()) {
                    val pathA = resA.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                    
                    // RUTA AZUL: Con micro-desvíos precisos y reducidos para ser más exacta
                    val waypointsForced = mutableListOf<String>()
                    for (i in 0 until paradas.size - 1) {
                        val p1 = paradas[i]
                        val p2 = paradas[i+1]
                        if (i > 0) waypointsForced.add("${p1.latitude},${p1.longitude}")
                        
                        val vLat = p2.latitude - p1.latitude
                        val vLng = p2.longitude - p1.longitude
                        val dist = Math.sqrt(vLat * vLat + vLng * vLng)

                        if (dist > 0.003) { 
                            val offsetScale = 0.0025 // Reducido para que la ruta alternativa sea más cercana
                            val midLat = (p1.latitude + p2.latitude) / 2 + (-vLng / dist * offsetScale)
                            val midLng = (p1.longitude + p2.longitude) / 2 + (vLat / dist * offsetScale)
                            waypointsForced.add("via:$midLat,$midLng")
                        }
                    }

                    val resB = DirectionsApi.newRequest(geoContext)
                        .mode(TravelMode.DRIVING).origin(origin).destination(destination)
                        .waypoints(*waypointsForced.toTypedArray())
                        .avoid(DirectionsApi.RouteRestriction.HIGHWAYS)
                        .departureTime(Instant.now()).await()

                    activity?.runOnUiThread {
                        puntosRutaRapida = pathA.toMutableList()
                        kmRutaRapida = resA.routes[0].legs.sumOf { it.distance.inMeters.toDouble() } / 1000.0
                        
                        if (resB.routes.isNotEmpty()) {
                            puntosRutaAlterna = resB.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }.toMutableList()
                            kmRutaAlterna = resB.routes[0].legs.sumOf { it.distance.inMeters.toDouble() } / 1000.0
                        } else {
                            puntosRutaAlterna = puntosRutaRapida.toMutableList()
                            kmRutaAlterna = kmRutaRapida
                        }
                        puntosCaminoSeleccionado = puntosRutaRapida
                        dibujarLineas()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
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

        if (puntosCaminoSeleccionado == puntosRutaRapida) {
            poliLineaRapida?.apply { color = colorVerde; width = 8f; zIndex = 10f; pattern = null }
            poliLineaAlterna?.apply { 
                color = ColorUtils.setAlphaComponent(colorAzul, 200)
                width = 16f; zIndex = 5f; pattern = PATTERN_ALTERNATE 
            }
        } else {
            poliLineaAlterna?.apply { color = colorAzul; width = 8f; zIndex = 10f; pattern = null }
            poliLineaRapida?.apply { 
                color = ColorUtils.setAlphaComponent(colorVerde, 200)
                width = 16f; zIndex = 5f; pattern = PATTERN_ALTERNATE 
            }
        }
    }

    private fun iniciarRuta() {
        if (puntosCaminoSeleccionado.isEmpty()) return
        rutaIniciada = true
        btnIniciarRuta.text = "Pausar Ruta"
        tvEstadoRuta.text = "En ruta..."
        btnAbandonarRuta.visibility = View.GONE

        if (markerVehiculo == null) {
            markerVehiculo = mMap.addMarker(MarkerOptions().position(puntosCaminoSeleccionado[ultimoIndiceRecorrido])
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).anchor(0.5f, 0.5f).zIndex(20f))
        }
        animar(ultimoIndiceRecorrido)
    }

    private fun pausarRuta() {
        rutaIniciada = false
        animatorVehiculo?.cancel()
        btnIniciarRuta.text = "Reanudar Ruta"
        tvEstadoRuta.text = "Ruta pausada"
        btnAbandonarRuta.visibility = View.VISIBLE
    }

    private fun animar(idx: Int) {
        if (!rutaIniciada || idx >= puntosCaminoSeleccionado.size - 1) return
        ultimoIndiceRecorrido = idx
        val start = puntosCaminoSeleccionado[idx]
        val end = puntosCaminoSeleccionado[idx + 1]
        animatorVehiculo = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                val pos = LatLng(start.latitude + (end.latitude - start.latitude) * f, start.longitude + (end.longitude - start.longitude) * f)
                markerVehiculo?.position = pos
                mMap.moveCamera(CameraUpdateFactory.newLatLng(pos))
                verificar(pos)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { if (rutaIniciada) animar(idx + 1) }
            })
            start()
        }
    }

    private fun verificar(pos: LatLng) {
        val paradas = viajeActivo?.puntosParada ?: return
        if (paradasCompletadas < paradas.size) {
            val p = paradas[paradasCompletadas]
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(pos.latitude, pos.longitude, p.latitude, p.longitude, dist)
            
            // Radio de detección: 50 metros para mayor precisión en la última parada
            val radioDeteccion = if (paradasCompletadas == paradas.size - 1) 40 else 75
            
            if (dist[0] < radioDeteccion) {
                paradasCompletadas++
                tvParadasCompletadas.text = "$paradasCompletadas/${paradas.size} completadas"
                
                if (paradasCompletadas >= paradas.size) {
                    rutaIniciada = false
                    animatorVehiculo?.cancel()
                    
                    // Al finalizar, solo queda UN botón: "Terminar ruta"
                    btnIniciarRuta.text = "Terminar ruta"
                    btnIniciarRuta.isEnabled = true
                    tvEstadoRuta.text = "¡Destino alcanzado!"
                    btnAbandonarRuta.visibility = View.GONE // Ocultamos el otro botón
                } else {
                    tvParadaActual.text = "Próxima: Parada ${paradasCompletadas + 1}"
                }
            }
        }
    }

    private fun terminarRuta(guardar: Boolean) {
        rutaIniciada = false
        animatorVehiculo?.cancel()
        val currentPos = markerVehiculo?.position
        poliLineaRapida?.remove()
        poliLineaAlterna?.remove()
        markerVehiculo?.remove()
        markerVehiculo = null

        if (currentPos != null && puntosCaminoSeleccionado.isNotEmpty()) {
            val traveledPoints = puntosCaminoSeleccionado.subList(0, ultimoIndiceRecorrido + 1).toMutableList()
            traveledPoints.add(currentPos)
            val remainingPoints = mutableListOf(currentPos)
            remainingPoints.addAll(puntosCaminoSeleccionado.subList(ultimoIndiceRecorrido + 1, puntosCaminoSeleccionado.size))
            val colorActual = if (puntosCaminoSeleccionado == puntosRutaRapida) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3")
            if (traveledPoints.size >= 2) mMap.addPolyline(PolylineOptions().addAll(traveledPoints).color(colorActual).width(8f).zIndex(10f))
            if (remainingPoints.size >= 2) mMap.addPolyline(PolylineOptions().addAll(remainingPoints).color(Color.LTGRAY).width(8f).zIndex(5f))
        }

        markersParadas.forEachIndexed { index, marker -> if (index >= paradasCompletadas) marker.alpha = 0.4f }
        
        if (guardar && viajeActivo != null) {
            guardarDatosFinalesEnFirebase()
            tvEstadoRuta.text = "Viaje Finalizado"
            actualizarEstadoDisponible()
        } else {
            tvEstadoRuta.text = "Ruta Interrumpida"
        }
        btnIniciarRuta.isEnabled = false
        btnAbandonarRuta.visibility = View.VISIBLE
        btnAbandonarRuta.text = "Salir"
    }

    private fun guardarDatosFinalesEnFirebase() {
        val viaje = viajeActivo ?: return
        val kmRecorridos = if (puntosCaminoSeleccionado == puntosRutaRapida) kmRutaRapida else kmRutaAlterna
        val costo = (kmRecorridos * 15).toInt() 
        val combustible = kmRecorridos * 0.12

        val updates = hashMapOf<String, Any>(
            "fechaFin" to com.google.firebase.Timestamp.now(),
            "distancia" to "%.1f km".format(kmRecorridos),
            "costo" to costo.toString(),
            "combustible" to "%.2f".format(combustible),
            "paradas" to paradasCompletadas
        )

        db.collection("viajes").document(viaje.id).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Historial actualizado", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarEstadoDisponible() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(userId).update("estado", "0")
    }

    private fun limpiarPantallaTotalmente() {
        mMap.clear()
        markersParadas.clear()
        ultimoIndiceRecorrido = 0
        paradasCompletadas = 0
        viajeActivo = null
        puntosRutaRapida.clear()
        puntosRutaAlterna.clear()
        puntosCaminoSeleccionado.clear()
        rutaIniciada = false
        animatorVehiculo?.cancel()
        markerVehiculo = null
        actualizarEstadoDisponible()
        btnIniciarRuta.isEnabled = false
        btnIniciarRuta.text = "Iniciar Ruta"
        btnAbandonarRuta.visibility = View.GONE
        tvEstadoRuta.text = "Sin asignar ruta"
        tvParadaActual.text = ""
        tvParadasCompletadas.text = "0/0 paradas completadas"
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(MONTERREY, 12f))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viajeListener?.remove()
        animatorVehiculo?.cancel()
    }
}
