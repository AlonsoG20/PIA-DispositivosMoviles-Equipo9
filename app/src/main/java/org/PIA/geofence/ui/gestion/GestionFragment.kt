package org.PIA.geofence.ui.gestion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        adapterUnidades = UnidadAdapter(emptyList())
        rvUnidades.layoutManager = LinearLayoutManager(context)
        rvUnidades.adapter = adapterUnidades

        headerUnidades.setOnClickListener {
            isUnidadesExpanded = !isUnidadesExpanded
            rvUnidades.visibility = if (isUnidadesExpanded) View.VISIBLE else View.GONE
            btnAdd.visibility = if (isUnidadesExpanded) View.VISIBLE else View.GONE
            iconUnidades.setImageResource(if (isUnidadesExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        // 3. Personal Registrado (Activo)
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

        // 4. Personal Inactivo
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
                val list = snapshot?.toObjects(Unidad::class.java) ?: emptyList()
                adapterUnidades.updateUnidades(list)
            }

        personalListener = db.collection("usuarios")
            .whereIn("rol", listOf("chofer", "despachador"))
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                
                // Personal Activo (Estado != "2")
                val activos = list.filter { it.estado != "2" }
                adapterPersonal.updateUsers(activos)
                
                // Personal Inactivo (Estado == "2")
                val inactivos = list.filter { it.estado == "2" }
                adapterInactivos.updateUsers(inactivos)
            }
    }

    private fun toggleUserStatus(user: User) {
        // "2" es Fuera de Turno. Cambiamos a "0" (Disponible) o viceversa.
        val newEstado = if (user.estado == "2") "0" else "2"
        
        db.collection("usuarios").document(user.id)
            .update("estado", newEstado)
            .addOnSuccessListener {
                val msg = if (newEstado == "2") "${user.nombre} ahora está Fuera de Turno" else "${user.nombre} ha entrado en Turno"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al actualizar turno", Toast.LENGTH_SHORT).show()
            }
    }

    private fun changeUserRole(user: User, newRole: String) {
        db.collection("usuarios").document(user.id)
            .update("rol", newRole)
            .addOnSuccessListener {
                Toast.makeText(context, "Rol de ${user.nombre} cambiado a ${newRole.replaceFirstChar { it.uppercase() }}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al cambiar rol", Toast.LENGTH_SHORT).show()
            }
    }

    private fun assignRole(user: User, newRole: String) {
        val updates = mutableMapOf<String, Any>("rol" to newRole)
        if (newRole == "chofer") {
            updates["estado"] = "0" // Por defecto disponible al asignar rol
        }

        db.collection("usuarios").document(user.id)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Rol asignado con éxito", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sinRolListener?.remove()
        unidadesListener?.remove()
        personalListener?.remove()
    }
}
