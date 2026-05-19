package org.PIA.geofence.ui.gestion

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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

    companion object {
        const val ARG_SECTION = "target_section"
        const val SECTION_PERSONAL = "personal"
        const val SECTION_SOLICITUDES = "solicitudes"
        const val SECTION_INSTRUCCIONES = "instrucciones"
        const val SECTION_FLOTA = "flota"
        const val SECTION_HISTORIAL = "historial"

        fun newInstance(section: String): GestionFragment {
            val fragment = GestionFragment()
            val args = Bundle()
            args.putString(ARG_SECTION, section)
            fragment.arguments = args
            return fragment
        }
    }

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
    
    // Filtros Instrucciones
    private lateinit var layoutFiltrosInstrucciones: View
    private lateinit var btnFiltroGralTodos: MaterialButton
    private lateinit var btnFiltroGralPendiente: MaterialButton
    private lateinit var btnFiltroGralGuardada: MaterialButton
    private lateinit var btnFiltroGralCompletada: MaterialButton
    private lateinit var btnFiltroGralDescartada: MaterialButton
    private lateinit var btnFiltroBloqueo: MaterialButton
    private lateinit var spinnerFiltroMes: Spinner

    // Adapters
    private lateinit var adapterSinRol: UsersSinRolAdapter
    private lateinit var adapterUnidades: UnidadAdapter
    private lateinit var adapterPersonal: PersonalGestionAdapter
    private lateinit var adapterInstrucciones: InstruccionAdapter

    // Estado filtros
    private var currentStatusFilter = "todos"
    private var currentBlockFilter = 0 // 0: Todos, 1: Excluir bloqueados, 2: Solo bloqueados
    private var currentMonthFilter = -1 // -1: Todos, 0-11: Enero-Diciembre
    private var allInstruccionesRaw: List<Instruccion> = emptyList()

    // Flag para saber si entramos directo a una sección o desde el menú
    private var startedFromMenu = true

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
        
        // Filtros UI
        layoutFiltrosInstrucciones = view.findViewById(R.id.layoutFiltrosInstrucciones)
        btnFiltroGralTodos = view.findViewById(R.id.btnFiltroGralTodos)
        btnFiltroGralPendiente = view.findViewById(R.id.btnFiltroGralPendiente)
        btnFiltroGralGuardada = view.findViewById(R.id.btnFiltroGralGuardada)
        btnFiltroGralCompletada = view.findViewById(R.id.btnFiltroGralCompletada)
        btnFiltroGralDescartada = view.findViewById(R.id.btnFiltroGralDescartada)
        btnFiltroBloqueo = view.findViewById(R.id.btnFiltroBloqueo)
        spinnerFiltroMes = view.findViewById(R.id.spinnerFiltroMes)

        rvGestionGeneric.layoutManager = LinearLayoutManager(context)

        setupMenuClicks(view)
        setupFiltrosInstrucciones()

        // Manejar navegación directa si viene de reportes
        arguments?.getString(ARG_SECTION)?.let { section ->
            startedFromMenu = false
            navigateToSection(section)
        } ?: run {
            startedFromMenu = true
        }
    }

    private fun setupMenuClicks(view: View) {
        view.findViewById<View>(R.id.menuSolicitudes).setOnClickListener {
            navigateToSection(SECTION_SOLICITUDES)
        }
        view.findViewById<View>(R.id.menuInstrucciones).setOnClickListener {
            navigateToSection(SECTION_INSTRUCCIONES)
        }
        view.findViewById<View>(R.id.menuFlota).setOnClickListener {
            navigateToSection(SECTION_FLOTA)
        }
        view.findViewById<View>(R.id.menuPersonal).setOnClickListener {
            navigateToSection(SECTION_PERSONAL)
        }
        view.findViewById<View>(R.id.menuHistorial).setOnClickListener {
            navigateToSection(SECTION_HISTORIAL)
        }
    }

    private fun navigateToSection(section: String) {
        when (section) {
            SECTION_SOLICITUDES -> {
                showSection("Solicitudes de Acceso")
                loadSolicitudes()
            }
            SECTION_INSTRUCCIONES -> {
                showSection("Instrucciones")
                layoutFiltrosInstrucciones.visibility = View.VISIBLE
                loadInstruccionesEnviadas()
                fabAdd.visibility = View.VISIBLE
                fabAdd.setOnClickListener { showInstructionDialogGlobal() }
            }
            SECTION_FLOTA -> {
                showSection("Control de Flota")
                loadFlota()
                fabAdd.visibility = View.VISIBLE
                fabAdd.setOnClickListener { showAddUnidadDialog() }
            }
            SECTION_PERSONAL -> {
                showSection("Gestión de Personal")
                loadPersonal()
            }
            SECTION_HISTORIAL -> {
                showSection("Historial de Viajes")
                rvGestionGeneric.visibility = View.GONE
                containerHistorial.visibility = View.VISIBLE
                childFragmentManager.beginTransaction()
                    .replace(R.id.layoutHistorialInGestion, HistorialFragment())
                    .commit()
            }
        }
    }

    private fun setupFiltrosInstrucciones() {
        btnFiltroGralTodos.setOnClickListener { updateStatusFilter("todos") }
        btnFiltroGralPendiente.setOnClickListener { updateStatusFilter("pendiente") }
        btnFiltroGralGuardada.setOnClickListener { updateStatusFilter("guardada") }
        btnFiltroGralCompletada.setOnClickListener { updateStatusFilter("completada") }
        btnFiltroGralDescartada.setOnClickListener { updateStatusFilter("descartada") }

        btnFiltroBloqueo.setOnClickListener {
            currentBlockFilter = (currentBlockFilter + 1) % 3
            updateBlockFilterUI()
            applyAllInstruccionesFilters()
        }

        val meses = arrayOf("Todos los meses", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, meses)
        spinnerFiltroMes.adapter = monthAdapter
        spinnerFiltroMes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMonthFilter = position - 1
                applyAllInstruccionesFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        updateStatusFilterUI()
        updateBlockFilterUI()
    }

    private fun updateStatusFilter(filter: String) {
        currentStatusFilter = filter
        updateStatusFilterUI()
        applyAllInstruccionesFilters()
    }

    private fun updateStatusFilterUI() {
        if (!isAdded) return
        val primary = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        val white = ContextCompat.getColor(requireContext(), android.R.color.white)

        val buttons = mapOf(
            "todos" to btnFiltroGralTodos,
            "pendiente" to btnFiltroGralPendiente,
            "guardada" to btnFiltroGralGuardada,
            "completada" to btnFiltroGralCompletada,
            "descartada" to btnFiltroGralDescartada
        )

        buttons.forEach { (key, btn) ->
            if (key == currentStatusFilter) {
                btn.setBackgroundColor(primary)
                btn.setTextColor(white)
            } else {
                btn.setBackgroundColor(white)
                btn.setTextColor(primary)
            }
        }
    }

    private fun updateBlockFilterUI() {
        if (!isAdded) return
        btnFiltroBloqueo.text = when (currentBlockFilter) {
            1 -> "Bloqueados: Excluir"
            2 -> "Bloqueados: Solo"
            else -> "Bloqueados: Todos"
        }
    }

    private fun applyAllInstruccionesFilters() {
        var filtered = allInstruccionesRaw

        // Filtro de Estado
        if (currentStatusFilter != "todos") {
            filtered = filtered.filter { it.estado == currentStatusFilter }
        }

        // Filtro de Bloqueo
        filtered = when (currentBlockFilter) {
            1 -> filtered.filter { it.bloqueado == 0 }
            2 -> filtered.filter { it.bloqueado == 1 }
            else -> filtered
        }

        // Filtro de Mes
        if (currentMonthFilter != -1) {
            filtered = filtered.filter { inst ->
                inst.fechaCreacion?.let {
                    val cal = Calendar.getInstance()
                    cal.time = it.toDate()
                    cal.get(Calendar.MONTH) == currentMonthFilter
                } ?: false
            }
        }

        adapterInstrucciones.updateData(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSection(titulo: String) {
        layoutMenu.visibility = View.GONE
        layoutDetalle.visibility = View.VISIBLE
        tvSubtitulo.text = titulo
        rvGestionGeneric.visibility = View.VISIBLE
        containerHistorial.visibility = View.GONE
        fabAdd.visibility = View.GONE
        layoutEmpty.visibility = View.GONE
        layoutFiltrosInstrucciones.visibility = View.GONE
        
        activeListener?.remove()
        (activity as? MainActivity)?.showBackButton(true)
    }

    fun onBackPressed(): Boolean {
        // Solo interceptamos si entramos desde el menú de este mismo fragmento.
        // Si entramos directo (desde KPIs), permitimos que la pila de fragmentos regrese a Reportes.
        if (layoutDetalle.visibility == View.VISIBLE && startedFromMenu) {
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
                    doc.toObject(Instruccion::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                
                allInstruccionesRaw = list
                applyAllInstruccionesFilters()
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
                "destinatarioNombre" to (despachador.nombreCompleto),
                "estado" to "pendiente",
                "bloqueado" to 0,
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
