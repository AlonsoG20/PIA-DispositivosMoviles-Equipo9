package org.PIA.geofence.ui.cuenta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.PIA.geofence.R
import org.PIA.geofence.ui.login.LoginActivity

class CuentaFragment : Fragment(R.layout.fragment_cuenta) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvDriverName: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var tvAssignedUnit: TextView
    private lateinit var tvCompletedRoutes: TextView
    private lateinit var cardStats: LinearLayout
    private lateinit var spacerStats: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvUserRole = view.findViewById(R.id.tvUserRole)
        tvAssignedUnit = view.findViewById(R.id.tvAssignedUnit)
        tvCompletedRoutes = view.findViewById(R.id.tvCompletedRoutes)
        cardStats = view.findViewById(R.id.cardStats)
        spacerStats = view.findViewById(R.id.spacerStats)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Ocultar por defecto hasta saber el rol
        tvAssignedUnit.visibility = View.GONE
        cardStats.visibility = View.GONE
        spacerStats.visibility = View.GONE

        loadUserData()
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nombre = document.getString("nombre") ?: ""
                    val apellidos = document.getString("apellidos") ?: ""
                    val rol = document.getString("rol") ?: ""
                    val nombreCompleto = "$nombre $apellidos".trim()

                    tvDriverName.text = if (nombreCompleto.isNotEmpty()) nombreCompleto else "Usuario"
                    tvUserRole.text = "Rol: $rol"

                    // Mostrar unidad y estadísticas solo si el rol es Chofer
                    if (rol.equals("chofer", ignoreCase = true)) {
                        tvAssignedUnit.visibility = View.VISIBLE
                        cardStats.visibility = View.VISIBLE
                        spacerStats.visibility = View.VISIBLE

                        // Cargar la unidad real si está en el documento
                        val unidad = document.getString("unidad")
                        if (unidad != null) {
                            tvAssignedUnit.text = "Unidad asignada: $unidad"
                        }

                        // Buscar sus rutas completadas
                        if (nombreCompleto.isNotEmpty()) {
                            loadCompletedRoutes(nombreCompleto)
                        } else {
                            tvCompletedRoutes.text = "Total de rutas realizadas: sin rutas completadas"
                        }
                    } else {
                        tvAssignedUnit.visibility = View.GONE
                        cardStats.visibility = View.GONE
                        spacerStats.visibility = View.GONE
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al cargar datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadCompletedRoutes(driverName: String) {
        db.collection("viajes")
            .whereEqualTo("nombreChofer", driverName)
            .whereEqualTo("estado", "completada")
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null && !documents.isEmpty) {
                    val count = documents.size()
                    tvCompletedRoutes.text = "Total de rutas realizadas: $count"
                } else {
                    tvCompletedRoutes.text = "Total de rutas realizadas: sin rutas completadas"
                }
            }
            .addOnFailureListener {
                tvCompletedRoutes.text = "Total de rutas realizadas: sin rutas completadas"
            }
    }
}