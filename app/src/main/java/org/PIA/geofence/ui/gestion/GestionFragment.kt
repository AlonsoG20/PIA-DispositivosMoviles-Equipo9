package org.PIA.geofence.ui.gestion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.PIA.geofence.MainActivity
import org.PIA.geofence.R
import org.PIA.geofence.data.Instruccion
import org.PIA.geofence.data.Unidad
import org.PIA.geofence.data.User
import org.PIA.geofence.ui.historial.HistorialFragment

class GestionFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    // Layouts
    private lateinit var layoutMenu: View
    private lateinit var layoutDetalle: View
    private lateinit var tvSubtitulo: TextView
    private lateinit var rvGestionGeneric: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var containerHistorial: View

    // Adapters
    private lateinit var adapterSinRol: UsersSinRolAdapter
    private lateinit var adapterUnidades: UnidadAdapter
    private lateinit var adapterPersonal: PersonalGestionAdapter
    private lateinit var adapterInstrucciones: InstruccionAdapter

    // Listeners
    private var activeListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_gestion, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        layoutMenu = view.findViewById(R.id.layoutMenuGestion)
        layoutDetalle = view.findViewById(R.id.layoutDetalleGestion)
        tvSubtitulo = view.findViewById(R.id.tvSubtituloGestion)
        rvGestionGeneric = view.findViewById(R.id.rvGestionGeneric)
        tvEmpty = view.findViewById(R.id.tvEmptyGestion)
        fabAdd = view.findViewById(R.id.fabAddGestion)
        containerHistorial = view.findViewById(R.id.layoutHistorialInGestion)

        rvGestionGeneric.layoutManager = LinearLayoutManager(context)

        setupMenuClicks(view)
    }

    private fun setupMenuClicks(view: View) {
        view.findViewById<View>(R.id.menuSolicitudes).setOnClickListener {
            showSection("Solicitudes de Acceso")
            loadSolicitudes()
        }
        view.findViewById<View>(R.id.menuInstrucciones).setOnClickListener {
            showSection("Instrucciones Enviadas")
            loadInstruccionesEnviadas()
        }
        view.findViewById<View>(R.id.menuFlota).setOnClickListener {
            showSection("Control de Flota")
            loadFlota()
            fabAdd.visibility = View.VISIBLE
            fabAdd.setOnClickListener { /* Lógica para añadir unidad */ }
        }
        view.findViewById<View>(R.id.menuPersonal).setOnClickListener {
            showSection("Gestión de Personal")
            loadPersonal()
        }
        view.findViewById<View>(R.id.menuHistorial).setOnClickListener {
            showSection("Historial de Viajes")
            rvGestionGeneric.visibility = View.GONE
            containerHistorial.visibility = View.VISIBLE
            // El HistorialFragment ya maneja su propia lógica, 
            // pero como está incluido con <include>, necesitamos asegurarnos que se inicialice.
            childFragmentManager.beginTransaction()
                .replace(R.id.containerGestionContent, HistorialFragment())
                .commit()
        }
    }

    private fun showSection(titulo: String) {
        layoutMenu.visibility = View.GONE
        layoutDetalle.visibility = View.VISIBLE
        tvSubtitulo.text = titulo
        rvGestionGeneric.visibility = View.VISIBLE
        containerHistorial.visibility = View.GONE
        fabAdd.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        
        activeListener?.remove()
        (activity as? MainActivity)?.showBackButton(true)
    }

    fun onBackPressed(): Boolean {
        if (layoutDetalle.visibility == View.VISIBLE) {
            layoutDetalle.visibility = View.GONE
            layoutMenu.visibility = View.VISIBLE
            (activity as? MainActivity)?.showBackButton(false)
            activeListener?.remove()
            return true
        }
        return false
    }

    private fun loadSolicitudes() {
        adapterSinRol = UsersSinRolAdapter(emptyList()) { user, role -> assignRole(user, role) }
        rvGestionGeneric.adapter = adapterSinRol
        activeListener = db.collection("usuarios")
            .whereEqualTo("rol", "sinRol")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                adapterSinRol.updateUsers(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun loadInstruccionesEnviadas() {
        adapterInstrucciones = InstruccionAdapter(emptyList())
        rvGestionGeneric.adapter = adapterInstrucciones
        val uid = auth.currentUser?.uid ?: return
        activeListener = db.collection("instrucciones")
            .whereEqualTo("remitenteId", uid)
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Instruccion::class.java) ?: emptyList()
                adapterInstrucciones.updateData(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun loadFlota() {
        adapterUnidades = UnidadAdapter(emptyList()) { /* dialog refuel */ }
        rvGestionGeneric.adapter = adapterUnidades
        activeListener = db.collection("unidades")
            .orderBy("placa", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(Unidad::class.java)?.apply { id = it.id } } ?: emptyList()
                adapterUnidades.updateUnidades(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun loadPersonal() {
        adapterPersonal = PersonalGestionAdapter(emptyList(), 
            { user -> toggleStatus(user) }, 
            { user, role -> changeRole(user, role) },
            { user -> showInstructionDialog(user) }
        )
        rvGestionGeneric.adapter = adapterPersonal
        activeListener = db.collection("usuarios")
            .whereIn("rol", listOf("chofer", "despachador"))
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                adapterPersonal.updateUsers(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun showInstructionDialog(despachador: User) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_send_instruction, null)
        val spinnerTask = dialogView.findViewById<Spinner>(R.id.spinnerTasks)
        val etComment = dialogView.findViewById<TextInputEditText>(R.id.etInstructionComment)
        
        val tasks = arrayOf(
            "Seleccionar tarea...",
            "Revisar mantenimiento de unidad",
            "Supervisar carga de combustible",
            "Verificar reporte de incidencia",
            "Asignar ruta prioritaria",
            "Otro (especificar en comentarios)"
        )
        
        spinnerTask.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tasks)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Instrucción para ${despachador.nombre}")
            .setView(dialogView)
            .setPositiveButton("Enviar") { _, _ ->
                val taskPos = spinnerTask.selectedItemPosition
                val comment = etComment.text.toString().trim()
                
                if (taskPos == 0 && comment.isEmpty()) {
                    Toast.makeText(context, "Debes seleccionar una tarea o escribir un comentario", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val finalMessage = if (taskPos != 0) {
                    "${tasks[taskPos]}${if (comment.isNotEmpty()) ": $comment" else ""}"
                } else comment

                sendInstruction(despachador, finalMessage)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendInstruction(despachador: User, mensaje: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid).get().addOnSuccessListener { doc ->
            val nombreG = doc.getString("nombre") ?: "Gerente"
            val instruccion = hashMapOf(
                "mensaje" to mensaje,
                "remitenteId" to uid,
                "remitenteNombre" to nombreG,
                "destinatarioId" to despachador.id,
                "estado" to "pendiente",
                "fechaCreacion" to Timestamp.now()
            )
            db.collection("instrucciones").add(instruccion).addOnSuccessListener {
                Toast.makeText(context, "Instrucción enviada correctamente", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper methods for status/role
    private fun toggleStatus(user: User) {
        val newStatus = if (user.estado == "2") "0" else "2"
        db.collection("usuarios").document(user.id).update("estado", newStatus)
    }

    private fun changeRole(user: User, newRole: String) {
        db.collection("usuarios").document(user.id).update("rol", newRole)
    }

    private fun assignRole(user: User, newRole: String) {
        db.collection("usuarios").document(user.id).update("rol", newRole, "estado", "0")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeListener?.remove()
    }
}
