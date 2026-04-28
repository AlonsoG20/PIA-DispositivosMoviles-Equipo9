package org.PIA.geofence.ui.gestion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.PIA.geofence.R
import org.PIA.geofence.data.Unidad
import org.PIA.geofence.data.User

class GestionFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    
    private lateinit var adapterSinRol: UsersSinRolAdapter
    private lateinit var adapterUnidades: UnidadAdapter
    private lateinit var adapterPersonal: PersonalGestionAdapter
    private lateinit var adapterInactivos: PersonalGestionAdapter

    private var sinRolListener: ListenerRegistration? = null
    private var unidadesListener: ListenerRegistration? = null
    private var personalListener: ListenerRegistration? = null

    private var isSinRolExpanded = false
    private var isUnidadesExpanded = false
    private var isPersonalExpanded = false
    private var isInactivosExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gestion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        setupSections(view)
        loadData()
        escucharAlertas()
    }

    private fun setupSections(view: View) {
        val headerSinRol = view.findViewById<LinearLayout>(R.id.layoutHeaderAsignar)
        val rvSinRol = view.findViewById<RecyclerView>(R.id.rvUsuariosSinRol)
        val iconSinRol = view.findViewById<ImageView>(R.id.ivExpandIcon)
        
        adapterSinRol = UsersSinRolAdapter(emptyList()) { user, newRole -> assignRole(user, newRole) }
        rvSinRol.layoutManager = LinearLayoutManager(context)
        rvSinRol.adapter = adapterSinRol

        headerSinRol.setOnClickListener {
            isSinRolExpanded = !isSinRolExpanded
            rvSinRol.visibility = if (isSinRolExpanded) View.VISIBLE else View.GONE
            iconSinRol.setImageResource(if (isSinRolExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        val headerUnidades = view.findViewById<LinearLayout>(R.id.layoutHeaderUnidades)
        val rvUnidades = view.findViewById<RecyclerView>(R.id.rvUnidades)
        val iconUnidades = view.findViewById<ImageView>(R.id.ivExpandIconUnidades)
        val btnAdd = view.findViewById<Button>(R.id.btnAddUnidad)

        adapterUnidades = UnidadAdapter(emptyList()) { unidad ->
            showRefuelDialog(unidad)
        }
        rvUnidades.layoutManager = LinearLayoutManager(context)
        rvUnidades.adapter = adapterUnidades

        headerUnidades.setOnClickListener {
            isUnidadesExpanded = !isUnidadesExpanded
            rvUnidades.visibility = if (isUnidadesExpanded) View.VISIBLE else View.GONE
            btnAdd.visibility = if (isUnidadesExpanded) View.VISIBLE else View.GONE
            iconUnidades.setImageResource(if (isUnidadesExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        val headerPersonal = view.findViewById<LinearLayout>(R.id.layoutHeaderPersonal)
        val rvPersonal = view.findViewById<RecyclerView>(R.id.rvPersonal)
        val iconPersonal = view.findViewById<ImageView>(R.id.ivExpandIconPersonal)

        adapterPersonal = PersonalGestionAdapter(
            emptyList(),
            { user -> toggleUserStatus(user) },
            { user, newRole -> changeUserRole(user, newRole) }
        )
        rvPersonal.layoutManager = LinearLayoutManager(context)
        rvPersonal.adapter = adapterPersonal

        headerPersonal.setOnClickListener {
            isPersonalExpanded = !isPersonalExpanded
            rvPersonal.visibility = if (isPersonalExpanded) View.VISIBLE else View.GONE
            iconPersonal.setImageResource(if (isPersonalExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        val headerInactivos = view.findViewById<LinearLayout>(R.id.layoutHeaderInactivos)
        val rvInactivos = view.findViewById<RecyclerView>(R.id.rvChoferesInactivos)
        val iconInactivos = view.findViewById<ImageView>(R.id.ivExpandIconInactivos)

        adapterInactivos = PersonalGestionAdapter(
            emptyList(),
            { user -> toggleUserStatus(user) },
            { user, newRole -> changeUserRole(user, newRole) }
        )
        rvInactivos.layoutManager = LinearLayoutManager(context)
        rvInactivos.adapter = adapterInactivos

        headerInactivos.setOnClickListener {
            isInactivosExpanded = !isInactivosExpanded
            rvInactivos.visibility = if (isInactivosExpanded) View.VISIBLE else View.GONE
            iconInactivos.setImageResource(if (isInactivosExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }
    }

    private fun showRefuelDialog(unidad: Unidad) {
        // CORRECCIÓN DEFINITIVA: No inflar el layout de "Añadir Unidad" para recargar gasolina.
        // Usamos un EditText simple creado por código para evitar duplicados.
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
            .setMessage("Nivel actual: ${unidad.gasolinaActual.toInt()}L / ${unidad.capacidadMaxima.toInt()}L")
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

    private fun loadData() {
        sinRolListener = db.collection("usuarios")
            .whereEqualTo("rol", "sinRol")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                adapterSinRol.updateUsers(list)
            }

        unidadesListener = db.collection("unidades")
            .orderBy("placa", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Unidad::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                adapterUnidades.updateUnidades(list)
            }

        personalListener = db.collection("usuarios")
            .whereIn("rol", listOf("chofer", "despachador"))
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                val activos = list.filter { it.estado != "2" }
                adapterPersonal.updateUsers(activos)
                val inactivos = list.filter { it.estado == "2" }
                adapterInactivos.updateUsers(inactivos)
            }
    }

    private fun escucharAlertas() {
        db.collection("alertas")
            .whereEqualTo("leida", false)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { dc ->
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val msg = dc.document.getString("mensaje") ?: "Alerta de unidad"
                        val eco = dc.document.getString("unidadEco") ?: ""
                        if (isAdded) {
                            Toast.makeText(context, "⚠️ ALERTA U-$eco: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
    }

    private fun toggleUserStatus(user: User) {
        val newEstado = if (user.estado == "2") "0" else "2"
        db.collection("usuarios").document(user.id).update("estado", newEstado)
    }

    private fun changeUserRole(user: User, newRole: String) {
        db.collection("usuarios").document(user.id).update("rol", newRole)
    }

    private fun assignRole(user: User, newRole: String) {
        val updates = mutableMapOf<String, Any>("rol" to newRole)
        if (newRole == "chofer") updates["estado"] = "0"
        db.collection("usuarios").document(user.id).update(updates)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sinRolListener?.remove()
        unidadesListener?.remove()
        personalListener?.remove()
    }
}
