package org.PIA.geofence.ui.gestion

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.PIA.geofence.R
import org.PIA.geofence.data.Instruccion
import org.PIA.geofence.data.PuntoInteres
import org.PIA.geofence.data.Unidad
import org.PIA.geofence.data.User
import org.PIA.geofence.ui.cuenta.UserViewModel
import java.util.Calendar

class ControlDespachadorFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val userViewModel: UserViewModel by activityViewModels()

    private lateinit var adapterUnidades: UnidadAdapter
    private lateinit var adapterChoferes: ChoferAdapter
    private lateinit var adapterPOI: PoiAdapter
    private lateinit var adapterInstrucciones: InstruccionAdapter

    private lateinit var tvEmptyFlota: TextView
    private lateinit var tvEmptyChoferes: TextView
    private lateinit var tvEmptyPOI: TextView
    private lateinit var tvEmptyInstrucciones: TextView

    private lateinit var btnFilterAll: MaterialButton
    private lateinit var btnFilterDisponible: MaterialButton
    private lateinit var btnFilterOcupado: MaterialButton
    private lateinit var btnFilterInactivo: MaterialButton

    private lateinit var btnInstFilterPendiente: MaterialButton
    private lateinit var btnInstFilterGuardada: MaterialButton
    private lateinit var btnInstFilterCompletada: MaterialButton
    private lateinit var btnInstFilterDescartada: MaterialButton

    private var allChoferes: List<User> = emptyList()
    private var allInstrucciones: List<Instruccion> = emptyList()
    private var currentFilter: String = "ALL" // "ALL", "0", "1", "2"
    private var currentInstFilter: String = "pendiente"
    private var userRole: String = ""

    private var unidadesListener: ListenerRegistration? = null
    private var choferesListener: ListenerRegistration? = null
    private var poiListener: ListenerRegistration? = null
    private var instruccionesListener: ListenerRegistration? = null

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
        auth = FirebaseAuth.getInstance()

        val rvUnidades = view.findViewById<RecyclerView>(R.id.rvFlotaDespachador)
        val rvChoferes = view.findViewById<RecyclerView>(R.id.rvChoferesDespachador)
        val rvPOI = view.findViewById<RecyclerView>(R.id.rvPOIDespachador)
        val rvInstrucciones = view.findViewById<RecyclerView>(R.id.rvInstruccionesDespachador)
        
        val btnAddUnidad = view.findViewById<Button>(R.id.btnAddUnidadDespachador)
        val btnAddPOI = view.findViewById<Button>(R.id.btnAddPOI)

        btnFilterAll = view.findViewById(R.id.btnFilterAll)
        btnFilterDisponible = view.findViewById(R.id.btnFilterDisponible)
        btnFilterOcupado = view.findViewById(R.id.btnFilterOcupado)
        btnFilterInactivo = view.findViewById(R.id.btnFilterInactivo)

        btnInstFilterPendiente = view.findViewById(R.id.btnInstFilterPendiente)
        btnInstFilterGuardada = view.findViewById(R.id.btnInstFilterGuardada)
        btnInstFilterCompletada = view.findViewById(R.id.btnInstFilterCompletada)
        btnInstFilterDescartada = view.findViewById(R.id.btnInstFilterDescartada)

        tvEmptyFlota = view.findViewById(R.id.tvEmptyFlota)
        tvEmptyChoferes = view.findViewById(R.id.tvEmptyChoferes)
        tvEmptyPOI = view.findViewById(R.id.tvEmptyPOI)
        tvEmptyInstrucciones = view.findViewById(R.id.tvEmptyInstrucciones)

        adapterUnidades = UnidadAdapter(emptyList()) { unidad ->
            showRefuelDialog(unidad)
        }
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

        adapterInstrucciones = InstruccionAdapter(emptyList(), { instruccion, nuevoEstado ->
            realizarAccionInstruccion(instruccion, nuevoEstado)
        }, null)
        rvInstrucciones.layoutManager = LinearLayoutManager(context)
        rvInstrucciones.adapter = adapterInstrucciones

        btnAddUnidad.setOnClickListener {
            showAddUnidadDialog()
        }

        btnAddPOI.setOnClickListener {
            startActivity(Intent(requireContext(), AddPoiActivity::class.java))
        }

        btnFilterAll.setOnClickListener { updateFilter("ALL") }
        btnFilterDisponible.setOnClickListener { updateFilter("0") }
        btnFilterOcupado.setOnClickListener { updateFilter("1") }
        btnFilterInactivo.setOnClickListener { updateFilter("2") }

        btnInstFilterPendiente.setOnClickListener { updateInstFilter("pendiente") }
        btnInstFilterGuardada.setOnClickListener { updateInstFilter("guardada") }
        btnInstFilterCompletada.setOnClickListener { updateInstFilter("completada") }
        btnInstFilterDescartada.setOnClickListener { updateInstFilter("descartada") }

        setupUserObserver()
        updateInstFilterVisuals()
        loadData()
    }

    private fun setupUserObserver() {
        userViewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                userRole = user.rol ?: ""
                if (userRole == "gerente") {
                    btnFilterInactivo.visibility = View.VISIBLE
                } else {
                    btnFilterInactivo.visibility = View.GONE
                }
                applyFilter() 
            }
        }
        
        val uid = auth.currentUser?.uid
        if (uid != null) {
            userViewModel.loadUser(uid)
        }
    }

    private fun realizarAccionInstruccion(instruccion: Instruccion, nuevoEstado: String) {
        if (instruccion.bloqueado == 1) {
            Toast.makeText(context, "Esta instrucción está bloqueada y no se puede modificar", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mutableMapOf<String, Any>(
            "estado" to nuevoEstado
        )

        if (instruccion.estado == "pendiente") {
            updates["primeraModificacion"] = Timestamp.now()
        }

        if (nuevoEstado == "completada") {
            updates["fechaCompletado"] = Timestamp.now()
        }

        db.collection("instrucciones").document(instruccion.id)
            .update(updates)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "Estado actualizado: $nuevoEstado", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRefuelDialog(unidad: Unidad) {
        val input = TextInputEditText(requireContext())
        input.hint = "Litros a cargar"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        
        val container = LinearLayout(requireContext())
        container.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(60, 20, 60, 0)
        input.layoutParams = lp
        container.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cargar Gasolina - U-${unidad.numeroEconomico}")
            .setMessage("Nivel actual: ${"%.1f".format(unidad.gasolinaActual)}L / ${unidad.capacidadMaxima.toInt()}L")
            .setView(container)
            .setPositiveButton("Cargar") { _, _ ->
                val litrosStr = input.text.toString()
                if (litrosStr.isNotEmpty()) {
                    val litros = litrosStr.toDoubleOrNull() ?: 0.0
                    val nuevaGasolina = (unidad.gasolinaActual + litros).coerceAtMost(unidad.capacidadMaxima)
                    
                    db.collection("unidades").document(unidad.id)
                        .update("gasolinaActual", nuevaGasolina)
                        .addOnSuccessListener {
                            if (isAdded) Toast.makeText(context, "Gasolina cargada: +$litros L", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateFilter(filter: String) {
        currentFilter = filter
        updateFilterVisuals()
        applyFilter()
    }

    private fun updateInstFilter(filter: String) {
        currentInstFilter = filter
        updateInstFilterVisuals()
        applyInstFilter()
    }

    private fun updateFilterVisuals() {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        val whiteColor = ContextCompat.getColor(requireContext(), android.R.color.white)

        listOf(btnFilterAll, btnFilterDisponible, btnFilterOcupado, btnFilterInactivo).forEach { btn ->
            btn.setBackgroundColor(whiteColor)
            btn.setTextColor(primaryColor)
        }

        val selectedBtn = when (currentFilter) {
            "ALL" -> btnFilterAll
            "0" -> btnFilterDisponible
            "1" -> btnFilterOcupado
            "2" -> btnFilterInactivo
            else -> btnFilterAll
        }
        selectedBtn.setBackgroundColor(primaryColor)
        selectedBtn.setTextColor(whiteColor)
    }

    private fun updateInstFilterVisuals() {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        val whiteColor = ContextCompat.getColor(requireContext(), android.R.color.white)

        listOf(btnInstFilterPendiente, btnInstFilterGuardada, btnInstFilterCompletada, btnInstFilterDescartada).forEach { btn ->
            btn.setBackgroundColor(whiteColor)
            btn.setTextColor(primaryColor)
        }

        val selectedBtn = when (currentInstFilter) {
            "pendiente" -> btnInstFilterPendiente
            "guardada" -> btnInstFilterGuardada
            "completada" -> btnInstFilterCompletada
            "descartada" -> btnInstFilterDescartada
            else -> btnInstFilterPendiente
        }
        selectedBtn.setBackgroundColor(primaryColor)
        selectedBtn.setTextColor(whiteColor)
    }

    private fun applyFilter() {
        val filteredList = when (currentFilter) {
            "ALL" -> allChoferes.filter { if (userRole != "gerente") it.estado != "2" else true }
            "0" -> allChoferes.filter { it.estado == "0" }
            "1" -> allChoferes.filter { it.estado == "1" }
            "2" -> allChoferes.filter { it.estado == "2" }
            else -> allChoferes
        }
        adapterChoferes.updateChoferes(filteredList)
        tvEmptyChoferes.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyInstFilter() {
        val filteredList = allInstrucciones.filter { it.estado == currentInstFilter }
        adapterInstrucciones.updateData(filteredList)
        tvEmptyInstrucciones.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun deletePoi(poi: PuntoInteres) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Punto")
            .setMessage("¿Estás seguro de eliminar ${poi.nombre}?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("puntosInteres").document(poi.id).delete()
                    .addOnSuccessListener {
                        if (isAdded) Toast.makeText(context, "Punto eliminado", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    private fun showAddUnidadDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_unidad, null)
        val etPlaca = dialogView.findViewById<TextInputEditText>(R.id.etPlaca)
        val etEconomico = dialogView.findViewById<TextInputEditText>(R.id.etEconomico)
        val etModelo = dialogView.findViewById<TextInputEditText>(R.id.etModelo)
        val etConsumo = dialogView.findViewById<TextInputEditText>(R.id.etConsumo)
        val etCapacidad = dialogView.findViewById<TextInputEditText>(R.id.etCapacidad)
        
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
            val consumo = etConsumo.text.toString().toDoubleOrNull() ?: 0.12
            val capacidad = etCapacidad.text.toString().toDoubleOrNull() ?: 100.0

            if (placa.isEmpty() || economico.isEmpty() || modelo.isEmpty()) {
                Toast.makeText(context, "Por favor llena los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevaUnidad = hashMapOf(
                "placa" to placa,
                "numeroEconomico" to economico,
                "modelo" to modelo,
                "estado" to "Disponible",
                "conductorAsignado" to "",
                "gasolinaActual" to capacidad,
                "capacidadMaxima" to capacidad,
                "consumoPorKm" to consumo,
                "ultimaActualizacion" to Timestamp.now()
            )

            db.collection("unidades").add(nuevaUnidad)
                .addOnSuccessListener {
                    if (isAdded) Toast.makeText(context, "Unidad registrada con éxito", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    if (isAdded) Toast.makeText(context, "Error al registrar unidad", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun loadData() {
        unidadesListener = db.collection("unidades")
            .orderBy("placa", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Unidad::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                adapterUnidades.updateUnidades(list)
                tvEmptyFlota.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }

        choferesListener = db.collection("usuarios")
            .whereEqualTo("rol", "chofer")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                allChoferes = list
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

        val currentUserId = auth.currentUser?.uid ?: return
        instruccionesListener = db.collection("instrucciones")
            .whereEqualTo("destinatarioId", currentUserId)
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ControlDespachador", "Error instrucciones: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Instruccion::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                
                checkAndProcessInstrucciones(list)
            }
    }

    private fun checkAndProcessInstrucciones(list: List<Instruccion>) {
        val now = Calendar.getInstance()
        val modifiedList = list.toMutableList()
        var hasChanges = false

        for (i in modifiedList.indices) {
            val inst = modifiedList[i]
            if (inst.bloqueado == 1) continue

            // 1. Pendientes > 24h -> descartada, bloqueado = 1
            if (inst.estado == "pendiente" && inst.fechaCreacion != null) {
                val cal = Calendar.getInstance()
                cal.time = inst.fechaCreacion.toDate()
                cal.add(Calendar.HOUR, 24)
                if (now.after(cal)) {
                    actualizarInstruccionExpirada(inst.id, "descartada")
                    modifiedList[i] = inst.copy(estado = "descartada", bloqueado = 1)
                    hasChanges = true
                    continue
                }
            }

            // 2. Modificada > 24h -> bloqueado = 1
            if (inst.primeraModificacion != null) {
                val cal = Calendar.getInstance()
                cal.time = inst.primeraModificacion.toDate()
                cal.add(Calendar.HOUR, 24)
                if (now.after(cal)) {
                    bloquearInstruccion(inst.id)
                    modifiedList[i] = inst.copy(bloqueado = 1)
                    hasChanges = true
                }
            }
        }

        allInstrucciones = modifiedList
        applyInstFilter()
    }

    private fun actualizarInstruccionExpirada(id: String, nuevoEstado: String) {
        db.collection("instrucciones").document(id)
            .update("estado", nuevoEstado, "bloqueado", 1)
    }

    private fun bloquearInstruccion(id: String) {
        db.collection("instrucciones").document(id)
            .update("bloqueado", 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unidadesListener?.remove()
        choferesListener?.remove()
        poiListener?.remove()
        instruccionesListener?.remove()
    }
}
