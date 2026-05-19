package org.PIA.geofence.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.PIA.geofence.R
import org.PIA.geofence.data.Incidencia
import org.PIA.geofence.data.Instruccion
import org.PIA.geofence.data.Viaje
import org.PIA.geofence.data.User
import org.PIA.geofence.ui.gestion.GestionFragment
import java.util.*

class ReportesFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: IncidenciaAdapter
    
    private lateinit var tvRutasHoy: TextView
    private lateinit var tvKmTotales: TextView
    private lateinit var tvIncidencias: TextView
    private lateinit var tvChoferes: TextView
    private lateinit var tvInstruccionesSemana: TextView

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
        tvInstruccionesSemana = view.findViewById(R.id.tvKpiInstruccionesSemana)

        setupRecyclerView(view)
        setupClickListeners(view)
        loadKPIs()
        loadIncidencias()
    }

    private fun setupRecyclerView(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvIncidencias)
        adapter = IncidenciaAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter
    }

    private fun setupClickListeners(view: View) {
        // Redirecciones dinámicas a Gestión
        view.findViewById<MaterialCardView>(R.id.cardChoferes).setOnClickListener {
            navigateToGestion(GestionFragment.SECTION_PERSONAL)
        }
        
        view.findViewById<MaterialCardView>(R.id.cardInstrucciones).setOnClickListener {
            navigateToGestion(GestionFragment.SECTION_INSTRUCCIONES)
        }

        view.findViewById<MaterialCardView>(R.id.cardRutasHoy).setOnClickListener {
            navigateToGestion(GestionFragment.SECTION_HISTORIAL)
        }
    }

    private fun navigateToGestion(section: String) {
        val fragment = GestionFragment.newInstance(section)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, R.id.nav_gestion.toString())
            .addToBackStack(null)
            .commit()
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
                val numDist = viaje.distancia.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                kmAcumuladosHoy += numDist
            }

            tvRutasHoy.text = viajesHoy.size.toString()
            tvKmTotales.text = String.format("%.1f", kmAcumuladosHoy)
        }

        // Contar choferes (Plantilla / Activos)
        db.collection("usuarios")
            .whereEqualTo("rol", "chofer")
            .addSnapshotListener { snapshot, _ ->
                val usuarios = snapshot?.toObjects(User::class.java) ?: emptyList()
                val plantilla = usuarios.size
                val activos = usuarios.count { it.estado == "0" }
                
                tvChoferes.text = "$plantilla / $activos"
            }

        // Cargar instrucciones de la semana (últimos 7 días)
        loadWeeklyInstructionsKPI()
    }

    private fun loadWeeklyInstructionsKPI() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val haceUnaSemana = calendar.time

        db.collection("instrucciones")
            .whereGreaterThanOrEqualTo("fechaCreacion", haceUnaSemana)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val list = snapshot?.toObjects(Instruccion::class.java) ?: emptyList()
                val enviadas = list.size
                val completadas = list.count { it.estado == "completada" }

                tvInstruccionesSemana.text = "$enviadas / $completadas"
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
