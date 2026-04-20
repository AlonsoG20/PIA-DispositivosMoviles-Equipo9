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
            if (!rutaIniciada) {
                iniciarRuta()
            } else {
                pausarRuta()
            }
        }

        btnAbandonarRuta.setOnClickListener {
            if (tvEstadoRuta.text == "Viaje Finalizado" || tvEstadoRuta.text == "Ruta Interrumpida" || tvEstadoRuta.text == "Pantalla limpia") {
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
                Toast.makeText(context, "Camino seleccionado", Toast.LENGTH_SHORT).show()
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

        trazarRutasDiferentes(paradas)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(paradas[0], 13f))
        btnIniciarRuta.isEnabled = true
        btnAbandonarRuta.visibility = View.VISIBLE
        btnAbandonarRuta.text = "Abandonar"
        tvEstadoRuta.text = "Viaje asignado: ${viaje.titulo}"
    }

    private fun trazarRutasDiferentes(paradas: List<LatLng>) {
        val geoContext = GeoApiContext.Builder().apiKey(apiKey).build()
        val origin = "${paradas.first().latitude},${paradas.first().longitude}"
        val destination = "${paradas.last().latitude},${paradas.last().longitude}"
        val waypoints = if (paradas.size > 2) {
            paradas.subList(1, paradas.size - 1).map { com.google.maps.model.LatLng(it.latitude, it.longitude) }.toTypedArray()
        } else emptyArray()

        Thread {
            try {
                val res1 = DirectionsApi.newRequest(geoContext)
                    .mode(TravelMode.DRIVING)
                    .origin(origin).destination(destination).waypoints(*waypoints)
                    .departureTime(Instant.now())
                    .alternatives(true).await()

                if (res1.routes.isNotEmpty()) {
                    activity?.runOnUiThread {
                        puntosRutaRapida = res1.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }.toMutableList()
                        puntosCaminoSeleccionado = puntosRutaRapida

                        if (res1.routes.size > 1) {
                            puntosRutaAlterna = res1.routes[1].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }.toMutableList()
                            dibujarLineas()
                        } else {
                            forzarRutaAlternativa(geoContext, origin, destination, waypoints)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun forzarRutaAlternativa(ctx: GeoApiContext, start: String, end: String, stops: Array<com.google.maps.model.LatLng>) {
        Thread {
            try {
                val res2 = DirectionsApi.newRequest(ctx).mode(TravelMode.DRIVING)
                    .origin(start).destination(end).waypoints(*stops)
                    .avoid(DirectionsApi.RouteRestriction.HIGHWAYS, DirectionsApi.RouteRestriction.TOLLS)
                    .departureTime(Instant.now()).await()

                if (res2.routes.isNotEmpty()) {
                    activity?.runOnUiThread {
                        puntosRutaAlterna = res2.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }.toMutableList()
                        dibujarLineas()
                    }
                } else {
                    activity?.runOnUiThread { dibujarLineas() }
                }
            } catch (e: Exception) { 
                activity?.runOnUiThread { dibujarLineas() }
            }
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
            poliLineaRapida?.apply { color = colorVerde; width = 12f; zIndex = 10f; pattern = null }
            poliLineaAlterna?.apply { color = ColorUtils.setAlphaComponent(colorAzul, 180); width = 24f; zIndex = 5f; pattern = PATTERN_ALTERNATE }
        } else {
            poliLineaAlterna?.apply { color = colorAzul; width = 12f; zIndex = 10f; pattern = null }
            poliLineaRapida?.apply { color = ColorUtils.setAlphaComponent(colorVerde, 180); width = 24f; zIndex = 5f; pattern = PATTERN_ALTERNATE }
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
        btnAbandonarRuta.text = "Abandonar"
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
        viajeActivo?.puntosParada?.forEachIndexed { i, p ->
            if (i >= paradasCompletadas) {
                val dist = FloatArray(1)
                android.location.Location.distanceBetween(pos.latitude, pos.longitude, p.latitude, p.longitude, dist)
                if (dist[0] < 75) {
                    paradasCompletadas = i + 1
                    tvParadasCompletadas.text = "$paradasCompletadas/${viajeActivo?.puntosParada?.size} completadas"
                    if (paradasCompletadas >= (viajeActivo?.puntosParada?.size ?: 0)) terminarRuta(true)
                }
            }
        }
    }

    private fun terminarRuta(guardar: Boolean) {
        rutaIniciada = false
        animatorVehiculo?.cancel()
        
        val currentPos = markerVehiculo?.position
        
        // Limpiar polilíneas activas y marcador de vehículo
        poliLineaRapida?.remove()
        poliLineaAlterna?.remove()
        markerVehiculo?.remove()
        markerVehiculo = null

        // Dibujar resumen visual si se inició el camino
        if (currentPos != null && puntosCaminoSeleccionado.isNotEmpty()) {
            val traveledPoints = puntosCaminoSeleccionado.subList(0, ultimoIndiceRecorrido + 1).toMutableList()
            traveledPoints.add(currentPos)
            
            val remainingPoints = mutableListOf(currentPos)
            remainingPoints.addAll(puntosCaminoSeleccionado.subList(ultimoIndiceRecorrido + 1, puntosCaminoSeleccionado.size))
            
            val colorActual = if (puntosCaminoSeleccionado == puntosRutaRapida) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3")
            
            // Camino recorrido (Color original)
            if (traveledPoints.size >= 2) {
                mMap.addPolyline(PolylineOptions().addAll(traveledPoints).color(colorActual).width(12f).zIndex(10f))
            }
            // Camino faltante (Gris)
            if (remainingPoints.size >= 2) {
                mMap.addPolyline(PolylineOptions().addAll(remainingPoints).color(Color.LTGRAY).width(12f).zIndex(5f))
            }
        }

        // Paradas faltantes en gris
        markersParadas.forEachIndexed { index, marker ->
            if (index >= paradasCompletadas) {
                marker.alpha = 0.4f
            }
        }
        
        if (guardar && viajeActivo != null) {
            db.collection("viajes").document(viajeActivo!!.id).update("fechaFin", com.google.firebase.Timestamp.now())
            tvEstadoRuta.text = "Viaje Finalizado"
            actualizarEstadoDisponible()
        } else {
            tvEstadoRuta.text = "Ruta Interrumpida"
        }
        
        btnIniciarRuta.text = "Iniciar Ruta"
        btnIniciarRuta.isEnabled = false
        btnAbandonarRuta.visibility = View.VISIBLE
        btnAbandonarRuta.text = "Salir"
        Toast.makeText(context, "Ruta finalizada definitivamente.", Toast.LENGTH_LONG).show()
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
