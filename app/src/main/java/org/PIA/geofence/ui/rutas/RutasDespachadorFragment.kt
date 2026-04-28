package org.PIA.geofence.ui.rutas

import android.graphics.Color
import android.location.Geocoder
import android.os.Build
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
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import org.PIA.geofence.R
import org.PIA.geofence.data.ParadaData
import org.PIA.geofence.data.PuntoInteres
import org.PIA.geofence.data.Unidad
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
    private lateinit var btnBuscarParada: ImageButton
    private lateinit var btnShowPOI: ImageView
    private lateinit var cardPOIDropdown: View
    private lateinit var rvPoiDropdown: RecyclerView
    private lateinit var poiSmallAdapter: PoiSmallAdapter

    private lateinit var btnSeleccionarUnidad: View
    private lateinit var tvUnidadSeleccionada: TextView
    private lateinit var cardUnidadesDropdown: View
    private lateinit var rvUnidadesDropdown: RecyclerView
    private lateinit var unidadCompactAdapter: UnidadCompactAdapter

    private var choferSeleccionado: User? = null
    private var unidadSeleccionada: Unidad? = null
    private var paradasSeleccionadas = mutableListOf<Marker>()
    private var poiMarkers = mutableListOf<Marker>()
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
        btnBuscarParada = view.findViewById(R.id.btnBuscarParadaText)
        btnShowPOI = view.findViewById(R.id.btnShowPOI)
        cardPOIDropdown = view.findViewById(R.id.cardPOIDropdown)
        rvPoiDropdown = view.findViewById(R.id.rvPoiDropdown)
        rvParadas = view.findViewById(R.id.rvParadasSeleccionadas)
        tvChoferInfo = view.findViewById(R.id.tvChoferSeleccionadoInfo)
        btnEnviar = view.findViewById(R.id.btnEnviarRutaFinal)

        btnSeleccionarUnidad = view.findViewById(R.id.btnSeleccionarUnidad)
        tvUnidadSeleccionada = view.findViewById(R.id.tvUnidadSeleccionada)
        cardUnidadesDropdown = view.findViewById(R.id.cardUnidadesDropdown)
        rvUnidadesDropdown = view.findViewById(R.id.rvUnidadesDropdown)
        
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

        poiSmallAdapter = PoiSmallAdapter(emptyList()) { poi ->
            val latLng = LatLng(poi.lugar?.latitude ?: 0.0, poi.lugar?.longitude ?: 0.0)
            agregarParada(latLng, poi.nombre)
            cardPOIDropdown.visibility = View.GONE
        }
        rvPoiDropdown.layoutManager = LinearLayoutManager(context)
        rvPoiDropdown.adapter = poiSmallAdapter

        unidadCompactAdapter = UnidadCompactAdapter(emptyList()) { unidad ->
            unidadSeleccionada = unidad
            tvUnidadSeleccionada.text = "Unidad: ${unidad.numeroEconomico} (${unidad.placa})"
            tvUnidadSeleccionada.setTextColor(Color.BLACK)
            cardUnidadesDropdown.visibility = View.GONE
        }
        rvUnidadesDropdown.layoutManager = LinearLayoutManager(context)
        rvUnidadesDropdown.adapter = unidadCompactAdapter

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapViewDespachador) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnBuscarParada.setOnClickListener {
            val query = etBuscar.text.toString().trim()
            if (query.isNotEmpty()) buscarDireccion(query)
        }

        btnShowPOI.setOnClickListener {
            cardPOIDropdown.visibility = if (cardPOIDropdown.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (cardPOIDropdown.visibility == View.VISIBLE) cargarPuntosInteresDropdown()
        }

        btnSeleccionarUnidad.setOnClickListener {
            cardUnidadesDropdown.visibility = if (cardUnidadesDropdown.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (cardUnidadesDropdown.visibility == View.VISIBLE) cargarUnidadesDisponibles()
        }

        btnEnviar.setOnClickListener { asignarRutaFinal() }

        cargarChoferesDisponibles()
    }

    private fun cargarUnidadesDisponibles() {
        db.collection("unidades")
            .whereEqualTo("estado", "Disponible")
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.toObjects(Unidad::class.java)
                unidadCompactAdapter.updateUnidades(list)
                if (list.isEmpty()) {
                    Toast.makeText(context, "No hay unidades disponibles", Toast.LENGTH_SHORT).show()
                    cardUnidadesDropdown.visibility = View.GONE
                }
            }
    }

    private fun cargarPuntosInteresDropdown() {
        db.collection("puntosInteres").get().addOnSuccessListener { snapshot ->
            val list = snapshot.documents.mapNotNull { it.toObject(PuntoInteres::class.java) }
            poiSmallAdapter.updatePois(list)
        }
    }

    private fun buscarDireccion(query: String) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val latLng = LatLng(addr.latitude, addr.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                agregarParada(latLng, addr.getAddressLine(0) ?: "Búsqueda")
            } else {
                Toast.makeText(context, "No se encontró el lugar", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error en la búsqueda", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarChoferesDisponibles() {
        db.collection("usuarios")
            .whereEqualTo("rol", "chofer")
            .addSnapshotListener { snapshot, _ ->
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
                            holder.tvName.text = user.nombreCompleto
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
        val tvName: TextView = view.findViewById(R.id.tvChoferNombre)
        val tvEstado: TextView = view.findViewById(R.id.tvChoferEstado)
    }

    private fun seleccionarChofer(user: User) {
        choferSeleccionado = user
        tvChoferInfo.text = "Asignando a: ${user.nombreCompleto}"
        layoutLista.visibility = View.GONE
        layoutMapa.visibility = View.VISIBLE
        unidadSeleccionada = null
        tvUnidadSeleccionada.text = "Seleccionar Unidad (Obligatorio)"
        if (::mMap.isInitialized) {
            limpiarMapa()
            cargarPuntosInteresMapa()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(25.6866, -100.3161), 12f))
        
        mMap.setOnMapClickListener { latLng -> 
            agregarParada(latLng) 
            cardPOIDropdown.visibility = View.GONE
            cardUnidadesDropdown.visibility = View.GONE
        }

        mMap.setOnMarkerClickListener { marker ->
            if (marker.snippet == "POI") {
                agregarParada(marker.position, marker.title)
                true
            } else false
        }
        
        cargarPuntosInteresMapa()
    }

    private fun cargarPuntosInteresMapa() {
        db.collection("puntosInteres").get().addOnSuccessListener { snapshot ->
            poiMarkers.forEach { it.remove() }
            poiMarkers.clear()
            snapshot.documents.forEach { doc ->
                val poi = doc.toObject(PuntoInteres::class.java)
                if (poi?.lugar != null) {
                    val latLng = LatLng(poi.lugar.latitude, poi.lugar.longitude)
                    val marker = mMap.addMarker(MarkerOptions()
                        .position(latLng)
                        .title(poi.nombre)
                        .snippet("POI")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))
                    marker?.let { poiMarkers.add(it) }
                }
            }
        }
    }

    private fun agregarParada(latLng: LatLng, nombre: String? = null) {
        val titulo = nombre ?: "Parada ${paradasSeleccionadas.size + 1}"
        val marker = mMap.addMarker(MarkerOptions().position(latLng).title(titulo).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
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
        val waypoints = paradasSeleccionadas.subList(1, paradasSeleccionadas.size - 1).map { com.google.maps.model.LatLng(it.position.latitude, it.position.longitude) }.toTypedArray()

        Thread {
            try {
                val result = DirectionsApi.newRequest(context).mode(TravelMode.DRIVING).origin(origin).destination(destination).waypoints(*waypoints).await()
                if (result.routes.isNotEmpty()) {
                    val path = result.routes[0].overviewPolyline.decodePath().map { LatLng(it.lat, it.lng) }
                    activity?.runOnUiThread {
                        poliLineaRuta?.remove()
                        poliLineaRuta = mMap.addPolyline(PolylineOptions().addAll(path).color(Color.parseColor("#4CAF50")).width(12f))
                    }
                }
            } catch (e: Exception) { Log.e("Despacho", "Error: ${e.message}") }
        }.start()
    }

    private fun asignarRutaFinal() {
        val chofer = choferSeleccionado ?: return
        val unidad = unidadSeleccionada ?: return
        
        if (unidad.gasolinaActual < 5.0) {
            Toast.makeText(context, "La unidad ${unidad.placa} no tiene suficiente gasolina (mínimo 5L)", Toast.LENGTH_LONG).show()
            return
        }

        val paradasData = paradasSeleccionadas.map { ParadaData(it.title ?: "Parada", it.position.latitude, it.position.longitude) }
        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        
        val nuevaRuta = hashMapOf(
            "nombre" to "Ruta $timestamp",
            "choferId" to chofer.id,
            "chofer" to chofer.nombreCompleto, 
            "unidadId" to unidad.id,
            "unidadPlaca" to unidad.placa,
            "estado" to "pendiente",
            "completado" to false, 
            "fechaCreacion" to Timestamp.now(),
            "paradas" to paradasData
        )

        db.collection("rutas").add(nuevaRuta).addOnSuccessListener {
            db.collection("usuarios").document(chofer.id).update("estado", "1")
            db.collection("unidades").document(unidad.id).update("estado", "En ruta")
            Toast.makeText(context, "Ruta asignada", Toast.LENGTH_SHORT).show()
            layoutMapa.visibility = View.GONE
            layoutLista.visibility = View.VISIBLE
            limpiarMapa()
        }
    }

    private fun limpiarMapa() {
        paradasSeleccionadas.forEach { it.remove() }
        paradasSeleccionadas.clear()
        poiMarkers.forEach { it.remove() }
        poiMarkers.clear()
        poliLineaRuta?.remove()
        paradaAdapter.updateData(paradasSeleccionadas)
    }

    private inner class PoiSmallAdapter(private var pois: List<PuntoInteres>, private val onPoiClick: (PuntoInteres) -> Unit) : RecyclerView.Adapter<PoiSmallAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val tvName: TextView = view.findViewById(R.id.tvPoiNameSmall) }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_poi_small, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val poi = pois[position]
            holder.tvName.text = poi.nombre
            holder.itemView.setOnClickListener { onPoiClick(poi) }
        }
        override fun getItemCount() = pois.size
        fun updatePois(newPois: List<PuntoInteres>) { pois = newPois; notifyDataSetChanged() }
    }

    private inner class UnidadCompactAdapter(private var unidades: List<Unidad>, private val onUnidadClick: (Unidad) -> Unit) : RecyclerView.Adapter<UnidadCompactAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvEco: TextView = view.findViewById(R.id.tvNumEconomicoCompact)
            val tvPlaca: TextView = view.findViewById(R.id.tvPlacaCompact)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_unidad_compact, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val u = unidades[position]
            holder.tvEco.text = u.numeroEconomico
            holder.tvPlaca.text = u.placa
            holder.itemView.setOnClickListener { onUnidadClick(u) }
        }
        override fun getItemCount() = unidades.size
        fun updateUnidades(newList: List<Unidad>) { unidades = newList; notifyDataSetChanged() }
    }
}
