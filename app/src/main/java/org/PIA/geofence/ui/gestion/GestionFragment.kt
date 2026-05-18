package org.PIA.geofence.ui.gestion

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
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
import java.util.Calendar

class GestionFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    // Layouts
    private lateinit var layoutMenu: View
    private lateinit var layoutDetalle: View
    private lateinit var tvSubtitulo: TextView
    private lateinit var rvGestionGeneric: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var tvEmptyText: TextView
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
        layoutEmpty = view.findViewById(R.id.tvEmptyGestion)
        tvEmptyText = view.findViewById(R.id.tvEmptyTextGestion)
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
            fabAdd.visibility = View.VISIBLE
            fabAdd.setOnClickListener { showInstructionDialogGlobal() }
        }
        view.findViewById<View>(R.id.menuFlota).setOnClickListener {
            showSection("Control de Flota")
            loadFlota()
            fabAdd.visibility = View.VISIBLE
            fabAdd.setOnClickListener { showAddUnidadDialog() }
        }
        view.findViewById<View>(R.id.menuPersonal).setOnClickListener {
            showSection("Gestión de Personal")
            loadPersonal()
        }
        view.findViewById<View>(R.id.menuHistorial).setOnClickListener {
            showSection("Historial de Viajes")
            rvGestionGeneric.visibility = View.GONE
            containerHistorial.visibility = View.VISIBLE
            childFragmentManager.beginTransaction()
                .replace(R.id.layoutHistorialInGestion, HistorialFragment())
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
        layoutEmpty.visibility = View.GONE
        
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
        tvEmptyText.text = "No hay solicitudes de acceso pendientes"
        adapterSinRol = UsersSinRolAdapter(emptyList(), 
            { user -> showRoleSelectionDialog(user) },
            { user -> rejectUser(user) }
        )
        rvGestionGeneric.adapter = adapterSinRol
        activeListener = db.collection("usuarios")
            .whereEqualTo("rol", "sinRol")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GestionFragment", "Error solicitudes: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                adapterSinRol.updateUsers(list)
                layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun showRoleSelectionDialog(user: User) {
        val roles = arrayOf("Chofer", "Despachador")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Asignar Rol a ${user.nombre}")
            .setItems(roles) { _, which ->
                val selectedRole = roles[which].lowercase()
                assignRole(user, selectedRole)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun rejectUser(user: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rechazar Solicitud")
            .setMessage("¿Estás seguro de rechazar a ${user.nombre}?")
            .setPositiveButton("Sí, eliminar") { _, _ ->
                db.collection("usuarios").document(user.id).delete()
                    .addOnSuccessListener { Toast.makeText(context, "Usuario rechazado", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun loadInstruccionesEnviadas() {
        tvEmptyText.text = "Aún no has enviado ninguna instrucción"
        adapterInstrucciones = InstruccionAdapter(emptyList(), 
            onDeleteClick = { instruccion -> deleteInstruccion(instruccion) }
        )
        rvGestionGeneric.adapter = adapterInstrucciones
        val uid = auth.currentUser?.uid ?: return
        
        activeListener = db.collection("instrucciones")
            .whereEqualTo("remitenteId", uid)
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GestionFragment", "Error instrucciones enviadas: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val ins = doc.toObject(Instruccion::class.java)?.apply { id = doc.id }
                    
                    // Lógica de caducidad: Si tiene más de 24h y sigue pendiente, marcar como no realizado
                    if (ins != null && ins.estado == "pendiente" && ins.fechaCreacion != null) {
                        val limit = Calendar.getInstance()
                        limit.add(Calendar.DAY_OF_YEAR, -1)
                        if (ins.fechaCreacion!!.toDate().before(limit.time)) {
                            db.collection("instrucciones").document(ins.id).update("estado", "no realizado")
                            return@mapNotNull ins.copy(estado = "no realizado")
                        }
                    }
                    ins
                } ?: emptyList()
                
                adapterInstrucciones.updateData(list)
                layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun deleteInstruccion(instruccion: Instruccion) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Instrucción")
            .setMessage("¿Deseas quitar esta instrucción de la lista?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("instrucciones").document(instruccion.id).delete()
                    .addOnSuccessListener { Toast.makeText(context, "Instrucción eliminada", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showInstructionDialogGlobal() {
        db.collection("usuarios")
            .whereEqualTo("rol", "despachador")
            .get()
            .addOnSuccessListener { snapshot ->
                val despachadores = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.apply { id = doc.id }
                }
                if (despachadores.isEmpty()) {
                    Toast.makeText(context, "No hay despachadores registrados", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                val names = despachadores.map { it.nombreCompleto }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Seleccionar Despachador")
                    .setItems(names) { _, which ->
                        showInstructionDialog(despachadores[which])
                    }
                    .show()
            }
    }

    private fun loadFlota() {
        tvEmptyText.text = "No hay unidades registradas en la flota"
        adapterUnidades = UnidadAdapter(emptyList()) { /* dialog refuel */ }
        rvGestionGeneric.adapter = adapterUnidades
        activeListener = db.collection("unidades")
            .orderBy("placa", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { it.toObject(Unidad::class.java)?.apply { id = it.id } } ?: emptyList()
                adapterUnidades.updateUnidades(list)
                layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun showAddUnidadDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_unidad, null)
        val etPlaca = dialogView.findViewById<TextInputEditText>(R.id.etPlaca)
        val etEconomico = dialogView.findViewById<TextInputEditText>(R.id.etEconomico)
        val etModelo = dialogView.findViewById<TextInputEditText>(R.id.etModelo)
        val etConsumo = dialogView.findViewById<TextInputEditText>(R.id.etConsumo)
        val etCapacidad = dialogView.findViewById<TextInputEditText>(R.id.etCapacidad)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancelar).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnGuardar).setOnClickListener {
            val placa = etPlaca.text.toString().trim()
            val economico = etEconomico.text.toString().trim()
            val modelo = etModelo.text.toString().trim()
            val consumo = etConsumo.text.toString().toDoubleOrNull() ?: 0.12
            val capacidad = etCapacidad.text.toString().toDoubleOrNull() ?: 100.0

            if (placa.isNotEmpty() && economico.isNotEmpty()) {
                val unidad = hashMapOf(
                    "placa" to placa,
                    "numeroEconomico" to economico,
                    "modelo" to modelo,
                    "consumoKmL" to consumo,
                    "capacidadTanque" to capacidad,
                    "estado" to "0",
                    "ubicacionActual" to null
                )
                db.collection("unidades").add(unidad).addOnSuccessListener {
                    Toast.makeText(context, "Unidad agregada", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Placa y Número Económico son obligatorios", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun loadPersonal() {
        tvEmptyText.text = "No hay personal registrado (choferes o despachadores)"
        adapterPersonal = PersonalGestionAdapter(emptyList(), 
            { user -> toggleStatus(user) }, 
            { user, role -> changeRole(user, role) }
        )
        rvGestionGeneric.adapter = adapterPersonal
        activeListener = db.collection("usuarios")
            .whereIn("rol", listOf("chofer", "despachador"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                adapterPersonal.updateUsers(list)
                layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
