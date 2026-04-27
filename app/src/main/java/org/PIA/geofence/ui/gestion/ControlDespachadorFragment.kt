package org.PIA.geofence.ui.gestion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.PIA.geofence.R
import org.PIA.geofence.data.PuntoInteres
import org.PIA.geofence.data.Unidad
import org.PIA.geofence.data.User

class ControlDespachadorFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapterUnidades: UnidadAdapter
    private lateinit var adapterChoferes: ChoferAdapter
    private lateinit var adapterPOI: PoiAdapter

    private lateinit var tvEmptyFlota: TextView
    private lateinit var tvEmptyChoferes: TextView
    private lateinit var tvEmptyPOI: TextView

    private lateinit var btnFilterAll: MaterialButton
    private lateinit var btnFilterDisponible: MaterialButton
    private lateinit var btnFilterOcupado: MaterialButton

    private var allChoferes: List<User> = emptyList()
    private var currentFilter: String = "ALL" // "ALL", "0", "1"

    private var unidadesListener: ListenerRegistration? = null
    private var choferesListener: ListenerRegistration? = null
    private var poiListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_control_despachador, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()

        val rvUnidades = view.findViewById<RecyclerView>(R.id.rvFlotaDespachador)
        val rvChoferes = view.findViewById<RecyclerView>(R.id.rvChoferesDespachador)
        val rvPOI = view.findViewById<RecyclerView>(R.id.rvPOIDespachador)
        
        val btnAddUnidad = view.findViewById<Button>(R.id.btnAddUnidadDespachador)
        val btnAddPOI = view.findViewById<Button>(R.id.btnAddPOI)

        btnFilterAll = view.findViewById(R.id.btnFilterAll)
        btnFilterDisponible = view.findViewById(R.id.btnFilterDisponible)
        btnFilterOcupado = view.findViewById(R.id.btnFilterOcupado)

        tvEmptyFlota = view.findViewById(R.id.tvEmptyFlota)
        tvEmptyChoferes = view.findViewById(R.id.tvEmptyChoferes)
        tvEmptyPOI = view.findViewById(R.id.tvEmptyPOI)

        adapterUnidades = UnidadAdapter(emptyList())
        rvUnidades.layoutManager = LinearLayoutManager(context)
        rvUnidades.adapter = adapterUnidades

        adapterChoferes = ChoferAdapter(emptyList())
        rvChoferes.layoutManager = LinearLayoutManager(context)
        rvChoferes.adapter = adapterChoferes

        adapterPOI = PoiAdapter(emptyList()) { poi ->
            deletePoi(poi)
        }
        rvPOI.layoutManager = LinearLayoutManager(context)
        rvPOI.adapter = adapterPOI

        btnAddUnidad.setOnClickListener {
            showAddUnidadDialog()
        }

        btnAddPOI.setOnClickListener {
            startActivity(Intent(requireContext(), AddPoiActivity::class.java))
        }

        btnFilterAll.setOnClickListener { updateFilter("ALL") }
        btnFilterDisponible.setOnClickListener { updateFilter("0") }
        btnFilterOcupado.setOnClickListener { updateFilter("1") }

        updateFilterVisuals()
        loadData()
    }

    private fun updateFilter(filter: String) {
        currentFilter = filter
        updateFilterVisuals()
        applyFilter()
    }

    private fun updateFilterVisuals() {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        val whiteColor = ContextCompat.getColor(requireContext(), android.R.color.white)

        // Reset all
        listOf(btnFilterAll, btnFilterDisponible, btnFilterOcupado).forEach { btn ->
            btn.setBackgroundColor(whiteColor)
            btn.setTextColor(primaryColor)
        }

        // Highlight selected
        val selectedBtn = when (currentFilter) {
            "ALL" -> btnFilterAll
            "0" -> btnFilterDisponible
            "1" -> btnFilterOcupado
            else -> btnFilterAll
        }
        selectedBtn.setBackgroundColor(primaryColor)
        selectedBtn.setTextColor(whiteColor)
    }

    private fun applyFilter() {
        val filteredList = when (currentFilter) {
            "ALL" -> allChoferes
            "0" -> allChoferes.filter { it.estado == "0" }
            "1" -> allChoferes.filter { it.estado == "1" }
            else -> allChoferes
        }
        adapterChoferes.updateChoferes(filteredList)
        tvEmptyChoferes.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun deletePoi(poi: PuntoInteres) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Punto")
            .setMessage("¿Estás seguro de eliminar ${poi.nombre}?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("puntosInteres").document(poi.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Punto eliminado", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    private fun showAddUnidadDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_unidad, null)
        val etPlaca = dialogView.findViewById<TextInputEditText>(R.id.etPlaca)
        val etEconomico = dialogView.findViewById<TextInputEditText>(R.id.etEconomico)
        val etModelo = dialogView.findViewById<TextInputEditText>(R.id.etModelo)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelar)
        val btnGuardar = dialogView.findViewById<Button>(R.id.btnGuardar)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val placa = etPlaca.text.toString().trim().uppercase()
            val economico = etEconomico.text.toString().trim()
            val modelo = etModelo.text.toString().trim()

            if (placa.isEmpty() || economico.isEmpty() || modelo.isEmpty()) {
                Toast.makeText(context, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevaUnidad = hashMapOf(
                "placa" to placa,
                "numeroEconomico" to economico,
                "modelo" to modelo,
                "estado" to "Disponible",
                "choferIdAsignado" to "",
                "nombreChoferAsignado" to "",
                "ultimaActualizacion" to Timestamp.now()
            )

            db.collection("unidades").add(nuevaUnidad)
                .addOnSuccessListener {
                    Toast.makeText(context, "Unidad registrada con éxito", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error al registrar unidad", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun loadData() {
        unidadesListener = db.collection("unidades")
            .orderBy("placa", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Unidad::class.java) ?: emptyList()
                adapterUnidades.updateUnidades(list)
                tvEmptyFlota.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }

        choferesListener = db.collection("usuarios")
            .whereEqualTo("rol", "chofer")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                // FILTRO: Solo activos (0 y 1)
                allChoferes = list.filter { it.estado != "2" }
                applyFilter()
            }

        poiListener = db.collection("puntosInteres")
            .orderBy("nombre", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PuntoInteres::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                adapterPOI.updatePois(list)
                tvEmptyPOI.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unidadesListener?.remove()
        choferesListener?.remove()
        poiListener?.remove()
    }
}