package org.PIA.geofence.ui.rutas

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import org.PIA.geofence.MainActivity
import org.PIA.geofence.R
import org.PIA.geofence.data.Ruta
import org.PIA.geofence.data.Unidad
import java.time.Instant
import java.util.*

class RutasFragment : Fragment(R.layout.fragment_rutas), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val viewModel: RutasViewModel by activityViewModels()

    private lateinit var tvEstadoRuta: TextView
    private lateinit var tvParadaActual: TextView
    private lateinit var tvParadasCompletadas: TextView
    private lateinit var btnAceptarRuta: Button
    private lateinit var btnIniciarRuta: Button
    private lateinit var btnFinalizarRuta: Button

    private var rutaListener: ListenerRegistration? = null
    
    private var markerVehiculo: Marker? = null
    private var markersParadas = mutableListOf<Marker>()
    private var poliLineaRapida: Polyline? = null
    private var poliLineaAlterna: Polyline? = null
    private var animatorVehiculo: ValueAnimator? = null

    private val apiKey = "AIzaSyAjjiNfvcx-3pSKpNULTceVeX46YxLfItc"
    private val PATTERN_ALTERNATE = listOf(Dash(40f), Gap(20f))
    private val MONTERREY = LatLng(25.6866, -100.3161)
    private val CHANNEL_ID = "alertas_geofence"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createNotificationChannel()

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
            val texto = btnIniciarRuta.text.toString()
            if (texto == "Terminar ruta") {
                finalizarRuta(true)
            } else if (texto == "Pausar Recorrido") {
                pausarRecorrido()
            } else {
                iniciarRecorrido()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Geofence"
            val descriptionText = "Canal para notificaciones de combustible y seguridad"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun enviarNotificacion(titulo: String, mensaje: String) {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reportes)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(requireContext())) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.rutaIniciada) {
            pausarRecorrido()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(MONTERREY, 12f))
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }

        mMap.setOnPolylineClickListener { polyline ->
            if (!viewModel.rutaIniciada && viewModel.ultimoIndiceRecorrido == 0) {
                val esRapida = polyline == poliLineaRapida
                viewModel.puntosCaminoSeleccionado = if (esRapida) viewModel.puntosRutaRapida else viewModel.puntosRutaAlterna
                viewModel.esRutaRapidaSeleccionada = esRapida
                actualizarEstilos()
                Toast.makeText(context, if (esRapida) "Ruta Principal seleccionada" else "Ruta Alterna seleccionada", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.rutaAsignada?.let { ruta ->
            actualizarUIConRuta(ruta)
            if (viewModel.puntosCaminoSeleccionado.isNotEmpty()) {
                dibujarLineas()
                recrearMarkersParadas(ruta)
                
                val pos = viewModel.puntosCaminoSeleccionado.getOrNull(viewModel.ultimoIndiceRecorrido) ?: viewModel.puntosCaminoSeleccionado[0]
                markerVehiculo = mMap.addMarker(MarkerOptions()
                    .position(pos)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .anchor(0.5f, 0.5f)
                    .zIndex(20f))
                
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f))
                actualizarUIParadaAlcanzada()
            }
        }
    }

    private fun recrearMarkersParadas(ruta: Ruta) {
        markersParadas.forEach { it.remove() }
        markersParadas.clear()
        ruta.paradas.forEachIndexed { index, parada ->
            val pos = LatLng(parada.latitud, parada.longitud)
            val marker = mMap.addMarker(MarkerOptions().position(pos).title(parada.nombre)
                .icon(BitmapDescriptorFactory.defaultMarker(if (index == 0) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_CYAN)))
            marker?.let { 
                if (index < viewModel.paradasCompletadas) it.alpha = 0.5f
                markersParadas.add(it) 
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
                    if (nuevaRuta?.id != viewModel.rutaAsignada?.id || nuevaRuta?.estado != viewModel.rutaAsignada?.estado) {
                        viewModel.rutaAsignada = nuevaRuta
                        actualizarUIConRuta(viewModel.rutaAsignada!!)
                    }
                } else {
                    viewModel.rutaAsignada = null
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
                if (viewModel.puntosCaminoSeleccionado.isEmpty()) {
                    trazarRutasRealmenteDiferentes(ruta)
                }
            }
            "en_progreso" -> {
                btnAceptarRuta.visibility = View.GONE
                btnIniciarRuta.visibility = View.VISIBLE
                if (viewModel.rutaIniciada) {
                    btnIniciarRuta.text = "Pausar Recorrido"
                    btnFinalizarRuta.visibility = View.GONE
                } else {
                    btnIniciarRuta.text = "Reanudar Recorrido"
                    btnFinalizarRuta.visibility = View.VISIBLE
                    btnFinalizarRuta.text = "Abandonar"
                }
                tvEstadoRuta.text = "Ruta en Progreso: ${ruta.nombre}"
                actualizarUIParadaAlcanzada()
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
        viewModel.puntosCaminoSeleccionado = emptyList()
        viewModel.ultimoIndiceRecorrido = 0
    }

    private fun aceptarRuta() {
        val ruta = viewModel.rutaAsignada ?: return
        val userId = auth.currentUser?.uid ?: return
        db.collection("rutas").document(ruta.id).update("estado", "aceptada").addOnSuccessListener {
            val historialData = hashMapOf(
                "choferId" to userId,
                "nombreChofer" to (ruta.choferNombre ?: ""),
                "despachadorId" to ruta.despachadorId,
                "nombreDespachador" to (ruta.despachadorNombre ?: ""),
                "unidadId" to (ruta.unidadId ?: ""),
                "placaUnidad" to (ruta.unidadPlaca ?: ""),
                "numeroEconomico" to (ruta.unidadEco ?: ""),
                "titulo" to ruta.nombre,
                "estado" to "aceptada",
                "fechaInicio" to com.google.firebase.Timestamp.now(),
                "distancia" to "0.0 km",
                "cantidadParadas" to 0,
                "costo" to "0",
                "combustible" to "0.0"
            )
            db.collection("viajes").document(ruta.id).set(historialData)
            db.collection("usuarios").document(userId).update("estado", "1")
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
                val resA = DirectionsApi.newRequest(geoContext).mode(TravelMode.DRIVING)
                    .origin(origin).destination(destination).waypoints(*waypointsNormal)
                    .departureTime(Instant.now()).await()

                if (resA.routes.isNotEmpty()) {
                    val pathA = resA.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                    
                    val waypointsForced = mutableListOf<String>()
                    for (i in 0 until paradasLatLng.size - 1) {
                        val p1 = paradasLatLng[i]
                        val p2 = paradasLatLng[i+1]
                        if (i > 0) waypointsForced.add("${p1.latitude},${p1.longitude}")
                        val vLat = p2.latitude - p1.latitude
                        val vLng = p2.longitude - p1.longitude
                        val dist = Math.sqrt(vLat * vLat + vLng * vLng)
                        if (dist > 0.0035) { 
                            val offsetScale = 0.0015 
                            val midLat = (p1.latitude + p2.latitude) / 2 + (-vLng / dist * offsetScale)
                            val midLng = (p1.longitude + p2.longitude) / 2 + (vLat / dist * offsetScale)
                            waypointsForced.add("via:$midLat,$midLng")
                        }
                    }

                    val resB = DirectionsApi.newRequest(geoContext).mode(TravelMode.DRIVING).origin(origin).destination(destination)
                        .waypoints(*waypointsForced.toTypedArray())
                        .avoid(DirectionsApi.RouteRestriction.HIGHWAYS)
                        .departureTime(Instant.now()).await()

                    activity?.runOnUiThread {
                        viewModel.puntosRutaRapida = pathA
                        viewModel.kmRutaRapida = resA.routes[0].legs.sumOf { it.distance.inMeters.toDouble() } / 1000.0
                        
                        if (resB.routes.isNotEmpty()) {
                            viewModel.puntosRutaAlterna = resB.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                            viewModel.kmRutaAlterna = resB.routes[0].legs.sumOf { it.distance.inMeters.toDouble() } / 1000.0
                        } else if (resA.routes.size > 1) {
                            viewModel.puntosRutaAlterna = resA.routes[1].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                            viewModel.kmRutaAlterna = resA.routes[1].legs.sumOf { it.distance.inMeters.toDouble() } / 1000.0
                        } else {
                            viewModel.puntosRutaAlterna = pathA
                            viewModel.kmRutaAlterna = viewModel.kmRutaRapida
                        }
                        
                        viewModel.puntosCaminoSeleccionado = viewModel.puntosRutaRapida
                        viewModel.esRutaRapidaSeleccionada = true
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
        poliLineaRapida = mMap.addPolyline(PolylineOptions().addAll(viewModel.puntosRutaRapida).clickable(true))
        poliLineaAlterna = mMap.addPolyline(PolylineOptions().addAll(viewModel.puntosRutaAlterna).clickable(true))
        actualizarEstilos()
    }

    private fun actualizarEstilos() {
        val colorVerde = Color.parseColor("#4CAF50")
        val colorAzul = Color.parseColor("#2196F3")
        
        if (viewModel.esRutaRapidaSeleccionada) {
            poliLineaRapida?.apply { color = colorVerde; width = 8f; zIndex = 10f; pattern = null }
            poliLineaAlterna?.apply { color = ColorUtils.setAlphaComponent(colorAzul, 200); width = 16f; zIndex = 5f; pattern = PATTERN_ALTERNATE }
        } else {
            poliLineaAlterna?.apply { color = colorAzul; width = 8f; zIndex = 10f; pattern = null }
            poliLineaRapida?.apply { color = ColorUtils.setAlphaComponent(colorVerde, 200); width = 16f; zIndex = 5f; pattern = PATTERN_ALTERNATE }
        }
    }

    private fun iniciarRecorrido() {
        if (viewModel.puntosCaminoSeleccionado.isEmpty()) return

        val unidadId = viewModel.rutaAsignada?.unidadId
        if (unidadId != null) {
            db.collection("unidades").document(unidadId).get().addOnSuccessListener { doc ->
                val unidad = doc.toObject(Unidad::class.java)
                if (unidad != null) {
                    val capacidadMaxima = unidad.capacidadMaxima
                    if (unidad.gasolinaActual < (capacidadMaxima * 0.25)) {
                        val choferNombre = viewModel.rutaAsignada?.choferNombre ?: "Desconocido"
                        val numeroEco = unidad.numeroEconomico
                        val msgDetalle = "Inicio de ruta: $choferNombre - Unidad $numeroEco - %.2f L restantes (bajo 25%%).".format(unidad.gasolinaActual)
                        
                        val incidencia = hashMapOf(
                            "chofer" to choferNombre,
                            "tipo" to "Gasolina Baja",
                            "detalle" to msgDetalle,
                            "fecha" to com.google.firebase.Timestamp.now(),
                            "nivelPrioridad" to "Alta"
                        )
                        db.collection("incidencias").add(incidencia)
                        enviarNotificacion("Combustible bajo al iniciar", "Unidad $numeroEco: %.2f L.".format(unidad.gasolinaActual))
                    }
                }
            }
        }

        viewModel.rutaIniciada = true
        btnIniciarRuta.text = "Pausar Recorrido"
        tvEstadoRuta.text = "En progreso"
        btnFinalizarRuta.visibility = View.GONE
        
        db.collection("rutas").document(viewModel.rutaAsignada!!.id).update("estado", "en_progreso")
        db.collection("viajes").document(viewModel.rutaAsignada!!.id).update("estado", "en progreso")
        
        viewModel.rutaAsignada?.unidadId?.let { uid ->
            db.collection("unidades").document(uid).update("estado", "En ruta")
        }
        
        if (markerVehiculo == null) {
            markerVehiculo = mMap.addMarker(MarkerOptions().position(viewModel.puntosCaminoSeleccionado[viewModel.ultimoIndiceRecorrido]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).anchor(0.5f, 0.5f).zIndex(20f))
        }
        animarVehiculo(viewModel.ultimoIndiceRecorrido)
    }

    private fun pausarRecorrido() {
        viewModel.rutaIniciada = false
        animatorVehiculo?.cancel()
        btnIniciarRuta.text = "Reanudar Recorrido"
        tvEstadoRuta.text = "Ruta pausada"
        btnFinalizarRuta.visibility = View.VISIBLE
        btnFinalizarRuta.text = "Abandonar"
    }

    private fun animarVehiculo(index: Int) {
        if (!viewModel.rutaIniciada || index >= viewModel.puntosCaminoSeleccionado.size - 1) return
        viewModel.ultimoIndiceRecorrido = index
        val inicio = viewModel.puntosCaminoSeleccionado[index]
        val fin = viewModel.puntosCaminoSeleccionado[index + 1]
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
                    if (viewModel.rutaIniciada) {
                        if (index + 1 < viewModel.puntosCaminoSeleccionado.size - 1) {
                            animarVehiculo(index + 1)
                        } else {
                            if (viewModel.paradasCompletadas == (viewModel.rutaAsignada?.paradas?.size ?: 0) - 1) {
                                forzarLlegadaMeta()
                            }
                        }
                    } 
                } 
            })
            start()
        }
    }

    private fun verificarLlegadaAParada(pos: LatLng) {
        val paradas = viewModel.rutaAsignada?.paradas ?: return
        if (viewModel.paradasCompletadas < paradas.size) {
            val p = paradas[viewModel.paradasCompletadas]
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(pos.latitude, pos.longitude, p.latitud, p.longitud, dist)
            
            val radio = if (viewModel.paradasCompletadas == paradas.size - 1) 20 else 75
            
            if (dist[0] < radio) {
                viewModel.paradasCompletadas++
                actualizarUIParadaAlcanzada()
            }
        }
    }

    private fun forzarLlegadaMeta() {
        val paradas = viewModel.rutaAsignada?.paradas ?: return
        viewModel.paradasCompletadas = paradas.size
        actualizarUIParadaAlcanzada()
    }

    private fun actualizarUIParadaAlcanzada() {
        val paradas = viewModel.rutaAsignada?.paradas ?: return
        tvParadasCompletadas.visibility = View.VISIBLE
        tvParadasCompletadas.text = "${viewModel.paradasCompletadas}/${paradas.size} completadas"
        
        // Actualizar visualmente los markers si es necesario
        markersParadas.forEachIndexed { i, m ->
            if (i < viewModel.paradasCompletadas) m.alpha = 0.5f
        }

        if (viewModel.paradasCompletadas >= paradas.size) {
            viewModel.rutaIniciada = false
            animatorVehiculo?.cancel()
            btnIniciarRuta.text = "Terminar ruta"
            btnIniciarRuta.isEnabled = true
            tvEstadoRuta.text = "¡Destino alcanzado!"
            btnFinalizarRuta.visibility = View.GONE
        } else {
            tvParadaActual.text = "Próxima: ${paradas[viewModel.paradasCompletadas].nombre}"
        }
    }

    private fun finalizarRuta(guardar: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val rutaId = viewModel.rutaAsignada?.id ?: return
        viewModel.rutaIniciada = false
        animatorVehiculo?.cancel()
        
        val currentPos = markerVehiculo?.position
        poliLineaRapida?.remove()
        poliLineaAlterna?.remove()
        markerVehiculo?.remove()
        markerVehiculo = null

        if (currentPos != null && viewModel.puntosCaminoSeleccionado.isNotEmpty()) {
            val traveled = viewModel.puntosCaminoSeleccionado.subList(0, viewModel.ultimoIndiceRecorrido + 1).toMutableList()
            traveled.add(currentPos)
            val remaining = mutableListOf(currentPos)
            remaining.addAll(viewModel.puntosCaminoSeleccionado.subList(viewModel.ultimoIndiceRecorrido + 1, viewModel.puntosCaminoSeleccionado.size))
            val color = if (viewModel.esRutaRapidaSeleccionada) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3")
            if (traveled.size >= 2) mMap.addPolyline(PolylineOptions().addAll(traveled).color(color).width(8f).zIndex(10f))
            if (remaining.size >= 2) mMap.addPolyline(PolylineOptions().addAll(remaining).color(Color.LTGRAY).width(8f).zIndex(5f))
        }

        markersParadas.forEachIndexed { i, m -> if (i >= viewModel.paradasCompletadas) m.alpha = 0.4f }
        
        if (guardar && viewModel.rutaAsignada != null) {
            actualizarHistorialFinal(rutaId)
            db.collection("usuarios").document(userId).update("estado", "0")
            viewModel.rutaAsignada?.unidadId?.let { uid ->
                db.collection("unidades").document(uid).update("estado", "Disponible")
            }
            tvEstadoRuta.text = "Viaje Finalizado"
        } else {
            tvEstadoRuta.text = "Ruta Interrumpida"
            db.collection("rutas").document(rutaId).update("estado", "cancelada")
            db.collection("usuarios").document(userId).update("estado", "0")
            viewModel.rutaAsignada?.unidadId?.let { uid ->
                db.collection("unidades").document(uid).update("estado", "Disponible")
            }
        }
        btnIniciarRuta.isEnabled = false
        btnFinalizarRuta.visibility = View.VISIBLE
        btnFinalizarRuta.text = "Salir"
    }

    private fun actualizarHistorialFinal(rutaId: String) {
        val unidadId = viewModel.rutaAsignada?.unidadId ?: return
        val choferNombre = viewModel.rutaAsignada?.choferNombre ?: "Desconocido"
        val numeroEco = viewModel.rutaAsignada?.unidadEco ?: "S/N"
        
        db.collection("unidades").document(unidadId).get().addOnSuccessListener { doc ->
            val unidad = doc.toObject(Unidad::class.java)
            val consumoBase = unidad?.consumoPorKm ?: 0.12
            val precioBase = 15.0 
            
            val km = if (viewModel.esRutaRapidaSeleccionada) viewModel.kmRutaRapida else viewModel.kmRutaAlterna
            val combustibleDouble = km * consumoBase
            val costoTotal = km * precioBase
            
            val gasolinaRestante = (unidad?.gasolinaActual ?: 0.0) - combustibleDouble

            val updates = hashMapOf(
                "estado" to "completada",
                "distancia" to "%.1f km".format(km),
                "cantidadParadas" to viewModel.paradasCompletadas,
                "costo" to costoTotal.toInt().toString(),
                "combustible" to "%.2f".format(combustibleDouble),
                "fechaFin" to com.google.firebase.Timestamp.now()
            )
            
            db.collection("rutas").document(rutaId).update("estado", "completada", "completado", true)
            db.collection("viajes").document(rutaId).update(updates as Map<String, Any>)
            
            db.collection("unidades").document(unidadId).update("gasolinaActual", gasolinaRestante)

            val capacidadMaxima = unidad?.capacidadMaxima ?: 100.0
            if (gasolinaRestante < (capacidadMaxima * 0.25)) {
                val msgDetalle = "Fin de ruta: $choferNombre - Unidad $numeroEco - %.2f L restantes (bajo 25%%).".format(gasolinaRestante)
                
                val incidencia = hashMapOf(
                    "chofer" to choferNombre,
                    "tipo" to "Gasolina Baja",
                    "detalle" to msgDetalle,
                    "fecha" to com.google.firebase.Timestamp.now(),
                    "nivelPrioridad" to "Alta"
                )
                db.collection("incidencias").add(incidencia)
                enviarNotificacion("Combustible bajo al finalizar", "Unidad $numeroEco llegó con solo %.2f L.".format(gasolinaRestante))
            }
        }
    }

    private fun actualizarEstadoDisponible() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(userId).update("estado", "0")
        viewModel.rutaAsignada?.unidadId?.let { uid ->
            db.collection("unidades").document(uid).update("estado", "Disponible")
        }
    }

    private fun limpiarPantallaTotalmente() {
        mMap.clear()
        markersParadas.clear()
        viewModel.ultimoIndiceRecorrido = 0
        viewModel.paradasCompletadas = 0
        viewModel.puntosCaminoSeleccionado = emptyList()
        viewModel.rutaIniciada = false
        animatorVehiculo?.cancel()
        markerVehiculo = null
        actualizarEstadoDisponible()
        mostrarSinRuta()
    }

    override fun onDestroyView() { 
        super.onDestroyView() 
        rutaListener?.remove() 
        animatorVehiculo?.cancel()
    }
}
