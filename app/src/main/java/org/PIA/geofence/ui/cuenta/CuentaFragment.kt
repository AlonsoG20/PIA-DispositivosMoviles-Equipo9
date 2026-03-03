package org.PIA.geofence.ui.cuenta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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
    private lateinit var tvCompletedRoutes: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvUserRole = view.findViewById(R.id.tvUserRole)
        tvCompletedRoutes = view.findViewById(R.id.tvCompletedRoutes)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        loadUserData()
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nombre = document.getString("nombre") ?: ""
                    val apellidos = document.getString("apellidos") ?: ""
                    val rol = document.getString("rol") ?: "Chofer"
                    val nombreCompleto = "$nombre $apellidos".trim()

                    tvDriverName.text = if (nombreCompleto.isNotEmpty()) nombreCompleto else "Usuario"
                    tvUserRole.text = "Rol: $rol"

                    // Una vez que tenemos el nombre del chofer, buscamos sus rutas completadas
                    if (nombreCompleto.isNotEmpty()) {
                        loadCompletedRoutes(nombreCompleto)
                    } else {
                        tvCompletedRoutes.text = "Total de rutas realizadas: sin rutas completadas"
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al cargar datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadCompletedRoutes(driverName: String) {
        db.collection("rutas")
            .whereEqualTo("chofer", driverName)
            .whereEqualTo("completado", true)
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