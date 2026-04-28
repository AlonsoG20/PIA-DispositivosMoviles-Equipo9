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
        rutaAsignada?.let { if (it.estado == "aceptada") trazarRutaEnMapa(it) }
    }

    private fun escucharRutasAsignadas() {
        val userId = auth.currentUser?.uid ?: return
        rutaListener = db.collection("rutas")
            .whereEqualTo("choferId", userId)
            .whereIn("estado", listOf("pendiente", "aceptada", "en_progreso"))
            .addSnapshotListener { snapshot, _ ->
                val rutaDoc = snapshot?.documents?.firstOrNull()
                if (rutaDoc != null) {
                    rutaAsignada = rutaDoc.toObject(Ruta::class.java)?.apply { id = rutaDoc.id }
                    actualizarUIConRuta(rutaAsignada!!)
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
        if (!isAdded) return
        tvEstadoRuta.text = "Sin rutas asignadas"
        tvParadaActual.text = "Espera a que un despachador te asigne trabajo"
        btnAceptarRuta.visibility = View.GONE
        btnIniciarRuta.visibility = View.GONE
        btnFinalizarRuta.visibility = View.GONE
        if (::mMap.isInitialized) mMap.clear()
        puntosCaminoReal.clear()
    }

    private fun aceptarRuta() {
        val ruta = rutaAsignada ?: return
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("rutas").document(ruta.id).update("estado", "aceptada").addOnSuccessListener {
            // REGISTRO INICIAL EN HISTORIAL (VIAJES) con datos de chofer y unidad
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
            if (isAdded) Toast.makeText(requireContext(), "Ruta aceptada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun trazarRutaEnMapa(ruta: Ruta) {
        if (!::mMap.isInitialized || ruta.paradas.isEmpty()) return
        mMap.clear()
        ruta.paradas.forEach { parada ->
            mMap.addMarker(MarkerOptions().position(LatLng(parada.latitud, parada.longitud)).title(parada.nombre).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
        }
        val context = GeoApiContext.Builder().apiKey(apiKey).build()
        val origin = "${ruta.paradas.first().latitud},${ruta.paradas.first().longitud}"
        val destination = "${ruta.paradas.last().latitud},${ruta.paradas.last().longitud}"
        val intermediate = ruta.paradas.subList(1, ruta.paradas.size - 1).map { com.google.maps.model.LatLng(it.latitud, it.longitud) }.toTypedArray()

        Thread {
            try {
                val result = DirectionsApi.newRequest(context).mode(TravelMode.DRIVING).origin(origin).destination(destination).waypoints(*intermediate).await()
                if (result.routes.isNotEmpty()) {
                    val path = result.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                    activity?.runOnUiThread {
                        puntosCaminoReal.clear()
                        puntosCaminoReal.addAll(path)
                        poliLineaRuta = mMap.addPolyline(PolylineOptions().addAll(path).color(Color.parseColor("#4CAF9E")).width(12f))
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(path[0], 14f))
                    }
                }
            } catch (e: Exception) { Log.e("Rutas", "Error trazar: ${e.message}") }
        }.start()
    }

    private fun iniciarRecorrido() {
        if (puntosCaminoReal.isEmpty()) return
        rutaIniciada = true
        paradasCompletadas = 0
        db.collection("rutas").document(rutaAsignada!!.id).update("estado", "en_progreso")
        db.collection("viajes").document(rutaAsignada!!.id).update("estado", "en progreso")
        markerVehiculo = mMap.addMarker(MarkerOptions().position(puntosCaminoReal[0]).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).anchor(0.5f, 0.5f))
        animarVehiculo(0)
    }

    private fun animarVehiculo(index: Int) {
        if (!rutaIniciada || index >= puntosCaminoReal.size - 1) { finalizarRuta(true); return }
        val inicio = puntosCaminoReal[index]
        val fin = puntosCaminoReal[index + 1]
        animatorVehiculo = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val f = animation.animatedValue as Float
                val pos = LatLng((fin.latitude-inicio.latitude)*f+inicio.latitude, (fin.longitude-inicio.longitude)*f+inicio.longitude)
                markerVehiculo?.position = pos
                mMap.moveCamera(CameraUpdateFactory.newLatLng(pos))
                verificarLlegadaAParada(pos)
            }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { if (rutaIniciada) animarVehiculo(index + 1) } })
            start()
        }
    }

    private fun verificarLlegadaAParada(pos: LatLng) {
        rutaAsignada?.paradas?.forEachIndexed { index, parada ->
            if (index > paradasCompletadas - 1) {
                val distance = FloatArray(1)
                android.location.Location.distanceBetween(pos.latitude, pos.longitude, parada.latitud, parada.longitud, distance)
                if (distance[0] < 50) onParadaAlcanzada(index)
            }
        }
    }

    private fun onParadaAlcanzada(index: Int) {
        if (index < paradasCompletadas) return
        paradasCompletadas = index + 1
        tvParadasCompletadas.visibility = View.VISIBLE
        tvParadasCompletadas.text = "$paradasCompletadas/${rutaAsignada?.paradas?.size ?: 0} paradas completadas"
    }

    private fun finalizarRuta(guardar: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val rutaId = rutaAsignada?.id ?: return
        val unidadId = rutaAsignada?.unidadId
        val choferNombre = rutaAsignada?.choferNombre ?: "Chofer"
        rutaIniciada = false
        
        val updatesRuta = hashMapOf(
            "estado" to "completada",
            "completado" to true,
            "fechaFin" to com.google.firebase.Timestamp.now()
        )
        db.collection("rutas").document(rutaId).update(updatesRuta as Map<String, Any>)
        
        db.collection("usuarios").document(userId).update("estado", "0")
        
        unidadId?.let { uid ->
            db.collection("unidades").document(uid).get().addOnSuccessListener { doc ->
                val u = doc.toObject(Unidad::class.java)
                if (u != null) {
                    val km = paradasCompletadas * 0.5
                    val consumption = km * 1.0 // 1 litro por cada 1 km
                    val nuevaGas = (u.gasolinaActual - consumption).coerceAtLeast(0.0)
                    
                    db.collection("unidades").document(uid).update("estado", "Disponible", "gasolinaActual", nuevaGas)
                    
                    // ALERTA DE TANQUE BAJO (RESERVA < 25%)
                    if (nuevaGas < (u.capacidadMaxima * 0.25)) {
                        val mensajeAlerta = "Unidad U-${u.numeroEconomico} con tanque bajo (${nuevaGas.toInt()}L)"
                        
                        // 1. Alerta para el panel de notificaciones rápidas
                        val alerta = hashMapOf(
                            "mensaje" to mensajeAlerta,
                            "unidadEco" to (u.numeroEconomico),
                            "fecha" to com.google.firebase.Timestamp.now(),
                            "leida" to false
                        )
                        db.collection("alertas").add(alerta)
                        
                        // 2. Incidencia para el apartado de "Alertas Geofencing" (ReportesFragment)
                        val incidencia = hashMapOf(
                            "chofer" to choferNombre,
                            "tipo" to "Tanque Bajo",
                            "detalle" to mensajeAlerta,
                            "fecha" to com.google.firebase.Timestamp.now(),
                            "nivelPrioridad" to "Alta"
                        )
                        db.collection("incidencias").add(incidencia)
                    }
                }
            }
        }

        if (guardar) actualizarHistorialFinal(rutaId)
        animatorVehiculo?.cancel()
        markerVehiculo?.remove()
        if (isAdded) Toast.makeText(requireContext(), "Ruta completada", Toast.LENGTH_SHORT).show()
    }

    private fun actualizarHistorialFinal(rutaId: String) {
        val km = paradasCompletadas * 0.5
        val combustible = km * 1.0 // 1 km = 1 litro
        val costo = combustible * 30 // 30 por cada litro
        val updates = hashMapOf(
            "estado" to "completada", // Usar minúsculas para coincidir con el filtro de CuentaFragment
            "distancia" to "%.1f".format(Locale.US, km),
            "cantidadParadas" to paradasCompletadas,
            "costo" to costo.toInt().toString(),
            "combustible" to "%.1f".format(Locale.US, combustible),
            "fechaFin" to com.google.firebase.Timestamp.now()
        )
        db.collection("viajes").document(rutaId).update(updates as Map<String, Any>)
    }

    override fun onDestroyView() { super.onDestroyView() ; rutaListener?.remove() }
}
