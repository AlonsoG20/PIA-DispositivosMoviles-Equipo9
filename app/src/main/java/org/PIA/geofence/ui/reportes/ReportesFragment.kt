package org.PIA.geofence.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.PIA.geofence.R
import org.PIA.geofence.data.Incidencia
import org.PIA.geofence.data.Viaje
import java.util.*

class ReportesFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: IncidenciaAdapter
    
    private lateinit var tvRutasHoy: TextView
    private lateinit var tvKmTotales: TextView
    private lateinit var tvIncidencias: TextView
    private lateinit var tvChoferes: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reportes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()

        // Inicializar vistas de KPIs
        tvRutasHoy = view.findViewById(R.id.tvKpiRutasHoy)
        tvKmTotales = view.findViewById(R.id.tvKpiKmTotales)
        tvIncidencias = view.findViewById(R.id.tvKpiIncidencias)
        tvChoferes = view.findViewById(R.id.tvKpiChoferes)

        setupRecyclerView(view)
        loadKPIs()
        loadIncidencias()
    }

    private fun setupRecyclerView(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvIncidencias)
        adapter = IncidenciaAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter
    }

    private fun loadKPIs() {
        // Cargar estadísticas filtradas por hoy
        db.collection("viajes").addSnapshotListener { snapshot, _ ->
            val viajes = snapshot?.toObjects(Viaje::class.java) ?: emptyList()
            
            var kmAcumuladosHoy = 0.0
            val hoy = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val viajesHoy = viajes.filter { it.fechaInicio?.toDate()?.after(hoy) == true }

            viajesHoy.forEach { viaje ->
                // Asumimos que la distancia viene como "X.X km" o similar, intentamos parsear solo el número
                val numDist = viaje.distancia.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                kmAcumuladosHoy += numDist
            }

            tvRutasHoy.text = viajesHoy.size.toString()
            tvKmTotales.text = String.format("%.1f", kmAcumuladosHoy)
        }

        // Contar choferes activos (rol chofer)
        db.collection("usuarios")
            .whereEqualTo("rol", "chofer")
            .addSnapshotListener { snapshot, _ ->
                tvChoferes.text = snapshot?.size()?.toString() ?: "0"
            }
    }

    private fun loadIncidencias() {
        // Cargar historial de alertas (incidencias)
        db.collection("incidencias")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Incidencia::class.java) ?: emptyList()
                adapter.updateData(list)
                tvIncidencias.text = list.size.toString()
            }
    }
}