package org.PIA.geofence.ui.historial

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.PIA.geofence.R
import org.PIA.geofence.data.Viaje
import java.util.*

class HistorialFragment : Fragment(R.layout.fragment_historial) {

    private lateinit var rvHistorial: RecyclerView
    private lateinit var tvContadorHoy: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ViajeAdapter
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvHistorial = view.findViewById(R.id.rvHistorial)
        tvContadorHoy = view.findViewById(R.id.tvContadorHoy)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        setupRecyclerView()
        cargarHistorial()
    }

    private fun setupRecyclerView() {
        adapter = ViajeAdapter(emptyList())
        rvHistorial.layoutManager = LinearLayoutManager(requireContext())
        rvHistorial.adapter = adapter
    }

    private fun cargarHistorial() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("viajes")
            .whereEqualTo("userId", userId)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener

                val listaViajes = value?.toObjects(Viaje::class.java) ?: emptyList()
                adapter.updateData(listaViajes)

                // Actualizar contador de hoy
                val hoy = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                val viajesHoy = listaViajes.count { 
                    it.fecha?.toDate()?.after(hoy) == true 
                }

                tvContadorHoy.text = "Viajes tomados hoy: $viajesHoy"
                
                if (listaViajes.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.GONE
                }
            }
    }
}