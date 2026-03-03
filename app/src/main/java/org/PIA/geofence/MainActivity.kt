package org.PIA.geofence

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.PIA.geofence.ui.login.SinRolFragment
import org.PIA.geofence.ui.cuenta.CuentaFragment
import org.PIA.geofence.ui.historial.HistorialFragment
import org.PIA.geofence.ui.rutas.RutasFragment
// Estos fragments deben ser creados
import org.PIA.geofence.ui.gestion.GestionFragment
import org.PIA.geofence.ui.reportes.ReportesFragment


class MainActivity : AppCompatActivity() {

    private lateinit var navCuenta: LinearLayout
    private lateinit var navRutas: LinearLayout
    private lateinit var navHistorial: LinearLayout
    private lateinit var navGestion: LinearLayout
    private lateinit var navReportes: LinearLayout
    private lateinit var navbar: ConstraintLayout

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navbar = findViewById(R.id.navbar)

        navCuenta = navbar.findViewById(R.id.nav_cuenta)
        navRutas = navbar.findViewById(R.id.nav_rutas)
        navHistorial = navbar.findViewById(R.id.nav_historial)
        navGestion = navbar.findViewById(R.id.nav_gestion)
        navReportes = navbar.findViewById(R.id.nav_reportes)

        checkUserRole()

        navCuenta.setOnClickListener { loadFragment(CuentaFragment(), it.id) }
        navRutas.setOnClickListener { loadFragment(RutasFragment(), it.id) }
        navHistorial.setOnClickListener { loadFragment(HistorialFragment(), it.id) }
        navGestion.setOnClickListener { loadFragment(GestionFragment(), it.id) }
        navReportes.setOnClickListener { loadFragment(ReportesFragment(), it.id) }
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rol = document.getString("rol") ?: "sinRol"
                    setupUIByRole(rol)
                }
            }
            .addOnFailureListener {
                navbar.visibility = View.GONE
                loadFragment(SinRolFragment())
            }
    }

    private fun setupUIByRole(rol: String) {
        navbar.visibility = View.VISIBLE
        
        when (rol) {
            "gerente" -> {
                navCuenta.visibility = View.VISIBLE
                navGestion.visibility = View.VISIBLE
                navReportes.visibility = View.VISIBLE
                
                navRutas.visibility = View.GONE
                navHistorial.visibility = View.GONE
                
                loadFragment(GestionFragment(), R.id.nav_gestion)
            }
            "chofer", "despachador" -> {
                navCuenta.visibility = View.VISIBLE
                navRutas.visibility = View.VISIBLE
                navHistorial.visibility = View.VISIBLE
                
                navGestion.visibility = View.GONE
                navReportes.visibility = View.GONE
                
                loadFragment(RutasFragment(), R.id.nav_rutas)
            }
            else -> {
                navbar.visibility = View.GONE
                loadFragment(SinRolFragment())
            }
        }
    }

    private fun loadFragment(fragment: Fragment, selectedId: Int = -1) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        
        if (selectedId != -1) {
            updateNavSelection(selectedId)
        }
    }

    private fun updateNavSelection(selectedId: Int) {
        navCuenta.isSelected = (selectedId == R.id.nav_cuenta)
        navRutas.isSelected = (selectedId == R.id.nav_rutas)
        navHistorial.isSelected = (selectedId == R.id.nav_historial)
        navGestion.isSelected = (selectedId == R.id.nav_gestion)
        navReportes.isSelected = (selectedId == R.id.nav_reportes)
    }
}