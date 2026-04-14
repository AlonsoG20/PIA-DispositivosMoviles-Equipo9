package org.PIA.geofence.ui.historial

import android.os.Bundle
import android.util.Log
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

        db.collection("usuarios").document(userId).get().addOnSuccessListener { userDoc ->
            val rol = userDoc.getString("rol") ?: ""
            Log.d("Historial", "Usuario rol: $rol")

            val collectionRef = db.collection("viajes")
            
            val query = if (rol == "despachador") {
                collectionRef.whereEqualTo("despachadorId", userId)
            } else {
                collectionRef.whereEqualTo("userId", userId)
            }

            // Quitamos el orderBy temporalmente para verificar si el problema es el índice compuesto
            query.addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("Historial", "Error en query: ${error.message}")
                    return@addSnapshotListener
                }

                val listaViajes = value?.toObjects(Viaje::class.java) ?: emptyList()
                Log.d("Historial", "Viajes recuperados: ${listaViajes.size}")

                // Ordenar manualmente en Kotlin para evitar problemas de índices en Firestore por ahora
                val listaOrdenada = listaViajes.sortedByDescending { it.fechaInicio }
                adapter.updateData(listaOrdenada)

                val conteoHoy = contarHoy(listaOrdenada)

                if (rol == "despachador") {
                    tvContadorHoy.text = "Viajes asignados hoy: $conteoHoy"
                    tvEmpty.text = "Aún no has asignado rutas"
                } else {
                    tvContadorHoy.text = "Viajes realizados hoy: $conteoHoy"
                    tvEmpty.text = "Aún no tienes viajes registrados"
                }

                tvEmpty.visibility = if (listaOrdenada.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun contarHoy(lista: List<Viaje>): Int {
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        return lista.count { viaje ->
            val fecha = viaje.fechaInicio?.toDate()
            fecha != null && !fecha.before(hoy)
        }
    }
}