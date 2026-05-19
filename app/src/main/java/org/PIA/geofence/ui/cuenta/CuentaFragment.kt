package org.PIA.geofence.ui.cuenta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.PIA.geofence.R
import org.PIA.geofence.ui.login.LoginActivity

class CuentaFragment : Fragment(R.layout.fragment_cuenta) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val userViewModel: UserViewModel by activityViewModels()

    private lateinit var tvDriverName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var chipUserRole: Chip
    private lateinit var tvAssignedUnit: TextView
    private lateinit var tvCompletedRoutes: TextView
    private lateinit var cardStats: View
    
    private var viajesListener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvUserEmail = view.findViewById(R.id.tvUserEmail)
        chipUserRole = view.findViewById(R.id.chipUserRole)
        tvAssignedUnit = view.findViewById(R.id.tvAssignedUnit)
        tvCompletedRoutes = view.findViewById(R.id.tvCompletedRoutes)
        cardStats = view.findViewById(R.id.cardStats)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)

        btnLogout.setOnClickListener {
            auth.signOut()
            userViewModel.clear()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Mostrar email desde Auth (siempre disponible)
        tvUserEmail.text = auth.currentUser?.email ?: "Sin correo"

        setupObservers()
        
        // Cargar datos si no están cargados (aunque MainActivity ya debería haberlo hecho)
        val userId = auth.currentUser?.uid
        if (userId != null) {
            userViewModel.loadUser(userId)
        }
    }

    private fun setupObservers() {
        userViewModel.userData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                val nombreCompleto = user.nombreCompleto
                tvDriverName.text = if (nombreCompleto.isNotEmpty() && nombreCompleto != "Sin nombre") {
                    nombreCompleto
                } else {
                    "Usuario"
                }
                
                val rol = user.rol ?: ""
                chipUserRole.text = rol.uppercase()

                // Cambiar color del chip según el rol
                when(rol.lowercase()) {
                    "gerente" -> chipUserRole.setChipBackgroundColorResource(android.R.color.holo_purple)
                    "despachador" -> chipUserRole.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
                    "chofer" -> chipUserRole.setChipBackgroundColorResource(R.color.teal_primary)
                }

                if (rol.equals("chofer", ignoreCase = true)) {
                    tvAssignedUnit.visibility = View.VISIBLE
                    cardStats.visibility = View.VISIBLE

                    val unidad = user.unidad
                    tvAssignedUnit.text = "Unidad asignada: ${unidad ?: "Sin unidad"}"

                    if (nombreCompleto.isNotEmpty() && nombreCompleto != "Sin nombre") {
                        setupCompletedRoutesListener(nombreCompleto)
                    } else {
                        tvCompletedRoutes.text = "Total de rutas realizadas: 0"
                    }
                } else {
                    tvAssignedUnit.visibility = View.GONE
                    cardStats.visibility = View.GONE
                }
            }
        }

        userViewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null && isAdded) {
                Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCompletedRoutesListener(driverName: String) {
        viajesListener?.remove()
        viajesListener = db.collection("viajes")
            .whereEqualTo("nombreChofer", driverName)
            .whereEqualTo("estado", "completada")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    tvCompletedRoutes.text = "Total de rutas realizadas: error"
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val count = snapshot.size()
                    tvCompletedRoutes.text = "Total de rutas realizadas: $count"
                } else {
                    tvCompletedRoutes.text = "Total de rutas realizadas: 0"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viajesListener?.remove()
    }
}
