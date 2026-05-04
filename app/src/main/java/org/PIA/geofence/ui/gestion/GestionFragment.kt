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

    private var sinRolListener: ListenerRegistration? = null
    private var unidadesListener: ListenerRegistration? = null
    private var personalListener: ListenerRegistration? = null

    private var isSinRolExpanded = false
    private var isUnidadesExpanded = false
    private var isPersonalExpanded = false

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
        // 1. Solicitudes (Sin Rol)
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

        // 2. Unidades (Flota)
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

        // 3. Personal Registrado
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
    }

    private fun showRefuelDialog(unidad: Unidad) {
        val context = requireContext()
        val input = TextInputEditText(context)
        input.hint = "Litros a cargar"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val margin = (24 * context.resources.displayMetrics.density).toInt()
        params.setMargins(margin, margin/2, margin, 0)
        input.layoutParams = params
        layout.addView(input)

        MaterialAlertDialogBuilder(context)
            .setTitle("Cargar Gasolina - U-${unidad.numeroEconomico}")
            .setMessage("Nivel actual: ${unidad.gasolinaActual.toInt()}L / ${unidad.capacidadMaxima.toInt()}L")
            .setView(layout)
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
                // El gerente puede ver a todos, incluso los inactivos (estado "2")
                adapterPersonal.updateUsers(list)
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
