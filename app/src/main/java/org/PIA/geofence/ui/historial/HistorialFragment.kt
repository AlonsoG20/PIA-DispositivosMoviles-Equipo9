package org.PIA.geofence.ui.historial

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.PIA.geofence.R
import org.PIA.geofence.data.Viaje
import java.util.*

class HistorialFragment : Fragment(R.layout.fragment_historial) {

    private lateinit var rvHistorial: RecyclerView
    private lateinit var tvContadorHoy: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    private lateinit var tilSearch: View
    private lateinit var layoutFilters: View
    private lateinit var chipGroupSort: ChipGroup
    private lateinit var btnResetFilters: View
    private lateinit var btnMoreFilters: View
    private lateinit var adapter: ViajeAdapter
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var todosLosViajes = listOf<Viaje>()
    private var currentRole = ""
    private var currentSortMode = "recent" // "recent", "distance", "fuel"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvHistorial = view.findViewById(R.id.rvHistorial)
        tvContadorHoy = view.findViewById(R.id.tvContadorHoy)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        etSearch = view.findViewById(R.id.etSearch)
        tilSearch = view.findViewById(R.id.tilSearch)
        layoutFilters = view.findViewById(R.id.layoutFilters)
        chipGroupSort = view.findViewById(R.id.chipGroupSort)
        btnResetFilters = view.findViewById(R.id.btnResetFilters)
        btnMoreFilters = view.findViewById(R.id.btnMoreFilters)

        setupRecyclerView()
        setupListeners()
        setupChipStyles()
        cargarHistorial()
    }

    private fun setupRecyclerView() {
        adapter = ViajeAdapter(emptyList())
        rvHistorial.layoutManager = LinearLayoutManager(requireContext())
        rvHistorial.adapter = adapter
    }

    private fun setupListeners() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltrosYOrden()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipGroupSort.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            when (checkedId) {
                R.id.chipRecent -> currentSortMode = "recent"
                R.id.chipDistance -> currentSortMode = "distance"
            }
            aplicarFiltrosYOrden()
        }

        btnMoreFilters.setOnClickListener { showMoreFiltersMenu() }
        
        btnResetFilters.setOnClickListener { resetFilters() }
    }

    private fun setupChipStyles() {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        
        // Azul transparente cuando está seleccionado (#332196F3), Blanco cuando no (#FFFFFF)
        val colors = intArrayOf(
            Color.parseColor("#332196F3"),
            Color.WHITE
        )
        val colorStateList = ColorStateList(states, colors)

        view?.findViewById<Chip>(R.id.chipRecent)?.chipBackgroundColor = colorStateList
        view?.findViewById<Chip>(R.id.chipDistance)?.chipBackgroundColor = colorStateList
    }

    private fun showMoreFiltersMenu() {
        val popup = PopupMenu(requireContext(), btnMoreFilters)
        popup.menu.add(0, 1, 0, "Más Recientes")
        popup.menu.add(0, 2, 1, "Más Distancia")
        popup.menu.add(0, 3, 2, "Más Combustible")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    currentSortMode = "recent"
                    chipGroupSort.check(R.id.chipRecent)
                }
                2 -> {
                    currentSortMode = "distance"
                    chipGroupSort.check(R.id.chipDistance)
                }
                3 -> {
                    currentSortMode = "fuel"
                    chipGroupSort.clearCheck() 
                }
            }
            aplicarFiltrosYOrden()
            true
        }
        popup.show()
    }

    private fun resetFilters() {
        etSearch.setText("")
        currentSortMode = "recent"
        chipGroupSort.check(R.id.chipRecent)
        aplicarFiltrosYOrden()
    }

    private fun cargarHistorial() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(currentUserId).get().addOnSuccessListener { userDoc ->
            currentRole = userDoc.getString("rol") ?: ""
            
            if (currentRole == "chofer") {
                tilSearch.visibility = View.GONE
            } else {
                tilSearch.visibility = View.VISIBLE
            }

            val collectionRef = db.collection("viajes")
            val query = when (currentRole) {
                "gerente" -> collectionRef
                "despachador" -> collectionRef.whereEqualTo("despachadorId", currentUserId)
                else -> collectionRef.whereEqualTo("choferId", currentUserId)
            }

            query.addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                todosLosViajes = value?.toObjects(Viaje::class.java) ?: emptyList()
                aplicarFiltrosYOrden()
            }
        }
    }

    private fun aplicarFiltrosYOrden() {
        val queryBusqueda = etSearch.text.toString().lowercase(Locale.getDefault())
        
        // Determinar si hay filtros activos para mostrar el botón de reset (X)
        val searchActive = queryBusqueda.isNotEmpty()
        val sortActive = currentSortMode != "recent"
        btnResetFilters.visibility = if (searchActive || sortActive) View.VISIBLE else View.GONE

        var filtrados = if (!searchActive || currentRole == "chofer") {
            todosLosViajes
        } else {
            todosLosViajes.filter { viaje ->
                viaje.nombreChofer.lowercase().contains(queryBusqueda) ||
                viaje.nombreDespachador.lowercase().contains(queryBusqueda) ||
                viaje.placaUnidad.lowercase().contains(queryBusqueda) ||
                viaje.numeroEconomico.lowercase().contains(queryBusqueda) ||
                viaje.titulo.lowercase().contains(queryBusqueda)
            }
        }

        val sortedList = when (currentSortMode) {
            "distance" -> filtrados.sortedByDescending { it.distancia.replace(" km", "").toDoubleOrNull() ?: 0.0 }
            "fuel" -> filtrados.sortedByDescending { it.combustible.toDoubleOrNull() ?: 0.0 }
            else -> filtrados.sortedByDescending { it.fechaInicio }
        }

        adapter.updateData(sortedList)
        actualizarUI(sortedList)
    }

    private fun actualizarUI(lista: List<Viaje>) {
        val conteoHoy = contarHoy(todosLosViajes)
        when (currentRole) {
            "despachador" -> tvContadorHoy.text = "Viajes asignados hoy: $conteoHoy"
            "gerente" -> tvContadorHoy.text = "Viajes totales hoy: $conteoHoy"
            else -> tvContadorHoy.text = "Viajes realizados hoy: $conteoHoy"
        }
        tvEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun contarHoy(lista: List<Viaje>): Int {
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        return lista.count { it.fechaInicio?.toDate()?.let { f -> !f.before(hoy) } == true && it.estado.equals("completada", true) }
    }
}
