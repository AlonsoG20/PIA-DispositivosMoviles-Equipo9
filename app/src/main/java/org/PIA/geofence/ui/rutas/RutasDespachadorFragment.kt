package org.PIA.geofence.ui.rutas

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import org.PIA.geofence.R
import org.PIA.geofence.data.User
import java.text.SimpleDateFormat
import java.util.*

class RutasDespachadorFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var layoutLista: View
    private lateinit var layoutMapa: View
    private lateinit var rvChoferes: RecyclerView
    private lateinit var etBuscar: EditText
    private lateinit var rvParadas: RecyclerView
    private lateinit var paradaAdapter: ParadaMiniAdapter
    private lateinit var tvChoferInfo: TextView
    private lateinit var btnEnviar: Button
    private lateinit var tvEmptyChoferes: TextView

    private var choferSeleccionado: User? = null
    private var paradasSeleccionadas = mutableListOf<Marker>()
    private var poliLineaRuta: Polyline? = null
    
    private val apiKey = "AIzaSyAjjiNfvcx-3pSKpNULTceVeX46YxLfItc"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_rutas_despachador, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutLista = view.findViewById(R.id.layoutListaChoferes)
        layoutMapa = view.findViewById(R.id.layoutMapaAsignacion)
        rvChoferes = view.findViewById(R.id.rvChoferesRutas)
        etBuscar = view.findViewById(R.id.etBuscarParada)
        rvParadas = view.findViewById(R.id.rvParadasSeleccionadas)
        tvChoferInfo = view.findViewById(R.id.tvChoferSeleccionadoInfo)
        btnEnviar = view.findViewById(R.id.btnEnviarRutaFinal)
        
        // Mensaje de lista vacía
        tvEmptyChoferes = TextView(context).apply {
            text = "No hay choferes disponibles"
            visibility = View.GONE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 50, 0, 0)
        }
        (layoutLista as LinearLayout).addView(tvEmptyChoferes)

        rvChoferes.layoutManager = LinearLayoutManager(context)
        
        paradaAdapter = ParadaMiniAdapter(paradasSeleccionadas) { position -> removeParada(position) }
        rvParadas.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvParadas.adapter = paradaAdapter

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapViewDespachador) as SupportMapFragment
        mapFragment.getMapAsync(this)

        view.findViewById<View>(R.id.btnBackToList).setOnClickListener {
            layoutMapa.visibility = View.GONE
            layoutLista.visibility = View.VISIBLE
        }

        btnEnviar.setOnClickListener { asignarRutaFinal() }

        cargarChoferesDisponibles()
    }

    private fun cargarChoferesDisponibles() {
        db.collection("usuarios")
            .whereEqualTo("rol", "chofer")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val todosLosChoferes = snapshot?.toObjects(User::class.java) ?: emptyList()
                val disponibles = todosLosChoferes.filter { it.estado == "0" }
                
                if (disponibles.isEmpty()) {
                    tvEmptyChoferes.visibility = View.VISIBLE
                    rvChoferes.visibility = View.GONE
                } else {
                    tvEmptyChoferes.visibility = View.GONE
                    rvChoferes.visibility = View.VISIBLE
                    val adapter = object : RecyclerView.Adapter<ChoferViewHolder>() {
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChoferViewHolder {
                            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chofer_simple, parent, false)
                            return ChoferViewHolder(v)
                        }
                        override fun onBindViewHolder(holder: ChoferViewHolder, position: Int) {
                            val user = disponibles[position]
                            holder.tvNombre.text = user.nombreCompleto
                            holder.tvEstado.text = "Disponible"
                            holder.itemView.setOnClickListener { seleccionarChofer(user) }
                        }
                        override fun getItemCount() = disponibles.size
                    }
                    rvChoferes.adapter = adapter
                }
            }
    }

    class ChoferViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvChoferNombre)
        val tvEstado: TextView = view.findViewById(R.id.tvChoferEstado)
        val vIndicator: View = view.findViewById(R.id.vEstadoIndicator)
    }

    private fun seleccionarChofer(user: User) {
        choferSeleccionado = user
        tvChoferInfo.text = "Asignando a: ${user.nombreCompleto}"
        layoutLista.visibility = View.GONE
        layoutMapa.visibility = View.VISIBLE
        limpiarMapa()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(25.6866, -100.3161), 12f))
        mMap.setOnMapClickListener { latLng -> agregarParada(latLng) }
    }

    private fun agregarParada(latLng: LatLng) {
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Parada ${paradasSeleccionadas.size + 1}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
        )
        marker?.let { 
            paradasSeleccionadas.add(it)
            paradaAdapter.updateData(paradasSeleccionadas)
            if (paradasSeleccionadas.size >= 2) trazarRuta()
        }
    }

    private fun removeParada(position: Int) {
        paradasSeleccionadas[position].remove()
        paradasSeleccionadas.removeAt(position)
        paradaAdapter.updateData(paradasSeleccionadas)
        if (paradasSeleccionadas.size >= 2) trazarRuta() else poliLineaRuta?.remove()
    }

    private fun trazarRuta() {
        val context = GeoApiContext.Builder().apiKey(apiKey).build()
        val origin = "${paradasSeleccionadas.first().position.latitude},${paradasSeleccionadas.first().position.longitude}"
        val destination = "${paradasSeleccionadas.last().position.latitude},${paradasSeleccionadas.last().position.longitude}"
        
        val waypoints = if (paradasSeleccionadas.size > 2) {
            paradasSeleccionadas.subList(1, paradasSeleccionadas.size - 1).map {
                com.google.maps.model.LatLng(it.position.latitude, it.position.longitude)
            }.toTypedArray()
        } else emptyArray()

        Thread {
            try {
                val result = DirectionsApi.newRequest(context).mode(TravelMode.DRIVING)
                    .origin(origin).destination(destination).waypoints(*waypoints).await()

                if (result.routes.isNotEmpty()) {
                    val path = result.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                    activity?.runOnUiThread {
                        poliLineaRuta?.remove()
                        poliLineaRuta = mMap.addPolyline(PolylineOptions().addAll(path).color(Color.parseColor("#4CAF9E")).width(12f))
                    }
                }
            } catch (e: Exception) { Log.e("Despacho", "Error ruta: ${e.message}") }
        }.start()
    }

    private fun asignarRutaFinal() {
        if (choferSeleccionado == null || paradasSeleccionadas.size < 2) {
            Toast.makeText(context, "Mínimo 2 paradas requeridas", Toast.LENGTH_SHORT).show()
            return
        }

        // CORRECCIÓN: Usamos GeoPoint y el nombre de campo correcto para que el chofer lo reciba
        val listaGeoPoints = paradasSeleccionadas.map {
            GeoPoint(it.position.latitude, it.position.longitude)
        }

        val viaje = hashMapOf(
            "despachadorId" to auth.currentUser?.uid,
            "userId" to choferSeleccionado?.id,
            "nombreChofer" to choferSeleccionado?.nombreCompleto,
            "titulo" to "Ruta Asignada ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}",
            "fechaInicio" to Timestamp.now(),
            "fechaFin" to null, // Importante para el filtro del chofer
            "estado" to "pendiente",
            "puntosParada" to listaGeoPoints, // Nombre de campo corregido
            "paradas" to paradasSeleccionadas.size
        )

        db.collection("viajes").add(viaje).addOnSuccessListener {
            db.collection("usuarios").document(choferSeleccionado!!.id).update("estado", "1")
            Toast.makeText(context, "Ruta enviada con éxito", Toast.LENGTH_SHORT).show()
            layoutMapa.visibility = View.GONE
            layoutLista.visibility = View.VISIBLE
        }
    }

    private fun limpiarMapa() {
        paradasSeleccionadas.forEach { it.remove() }
        paradasSeleccionadas.clear()
        poliLineaRuta?.remove()
        paradaAdapter.updateData(paradasSeleccionadas)
    }
}
