package org.PIA.geofence

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.ImageButton
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
import org.PIA.geofence.ui.rutas.RutasDespachadorFragment
import org.PIA.geofence.ui.gestion.GestionFragment
import org.PIA.geofence.ui.reportes.ReportesFragment
import org.PIA.geofence.ui.gestion.ControlDespachadorFragment


class MainActivity : AppCompatActivity() {

    private lateinit var navCuenta: LinearLayout
    private lateinit var navRutas: LinearLayout
    private lateinit var navHistorial: LinearLayout
    private lateinit var navGestion: LinearLayout
    private lateinit var navReportes: LinearLayout
    private lateinit var navbar: ConstraintLayout
    private lateinit var btnBack: ImageButton
    
    // Header user info views
    private lateinit var tvUserName: TextView
    private lateinit var tvUserRole: TextView

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
        
        // Inicializar vistas del header
        val header = findViewById<ConstraintLayout>(R.id.header)
        btnBack = header.findViewById(R.id.btn_back)
        tvUserName = header.findViewById(R.id.tv_user_name)
        tvUserRole = header.findViewById(R.id.tv_user_role)
        
        btnBack.setOnClickListener {
            // Manejar navegación hacia atrás para fragmentos anidados si es necesario
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (currentFragment is GestionFragment) {
                if (!currentFragment.onBackPressed()) {
                    onBackPressedDispatcher.onBackPressed()
                }
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        navCuenta = navbar.findViewById(R.id.nav_cuenta)
        navRutas = navbar.findViewById(R.id.nav_rutas)
        navHistorial = navbar.findViewById(R.id.nav_historial)
        navGestion = navbar.findViewById(R.id.nav_gestion)
        navReportes = navbar.findViewById(R.id.nav_reportes)

        checkUserRole()

        navCuenta.setOnClickListener { loadFragment(CuentaFragment(), it.id) }
        navHistorial.setOnClickListener { loadFragment(HistorialFragment(), it.id) }
        navRutas.setOnClickListener {
            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            db.collection("usuarios").document(userId).get().addOnSuccessListener { doc ->
                val rol = doc.getString("rol")
                if (rol == "despachador") {
                    loadFragment(RutasDespachadorFragment(), it.id)
                } else {
                    loadFragment(RutasFragment(), it.id)
                }
            }
        }
        navGestion.setOnClickListener { 
            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            db.collection("usuarios").document(userId).get().addOnSuccessListener { doc ->
                val rol = doc.getString("rol")
                if (rol == "despachador") {
                    loadFragment(ControlDespachadorFragment(), it.id)
                } else {
                    loadFragment(GestionFragment(), it.id)
                }
            }
        }
        navReportes.setOnClickListener { loadFragment(ReportesFragment(), it.id) }
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rol = document.getString("rol") ?: "sinRol"
                    val nombre = document.getString("nombre") ?: ""
                    val apellidos = document.getString("apellidos") ?: ""
                    
                    // Actualizar información en el header
                    tvUserName.text = if (nombre.isNotEmpty()) "$nombre $apellidos" else "Usuario"
                    tvUserRole.text = rol.replaceFirstChar { it.uppercase() }

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
        
        // Resetear visibilidades
        navCuenta.visibility = View.VISIBLE
        navRutas.visibility = View.GONE
        navHistorial.visibility = View.GONE
        navGestion.visibility = View.GONE
        navReportes.visibility = View.GONE

        // Ajustar textos de la navbar para el despachador
        val tvGestion = navbar.findViewById<TextView>(R.id.tvNavGestion)
        val ivGestion = navbar.findViewById<ImageView>(R.id.ivNavGestion)

        when (rol) {
            "gerente" -> {
                tvGestion.text = "Gestión"
                navGestion.visibility = View.VISIBLE
                navHistorial.visibility = View.GONE // SE QUITA LA PESTAÑA PARA MOVERLA DENTRO DE GESTIÓN
                navReportes.visibility = View.VISIBLE
                loadFragment(GestionFragment(), R.id.nav_gestion)
            }
            "despachador" -> {
                tvGestion.text = "Control"
                navGestion.visibility = View.VISIBLE
                navRutas.visibility = View.VISIBLE
                navHistorial.visibility = View.VISIBLE
                loadFragment(ControlDespachadorFragment(), R.id.nav_gestion)
            }
            "chofer" -> {
                navRutas.visibility = View.VISIBLE
                navHistorial.visibility = View.VISIBLE
                loadFragment(RutasFragment(), R.id.nav_rutas)
            }
            else -> {
                navbar.visibility = View.GONE
                loadFragment(SinRolFragment())
            }
        }
    }

    private fun loadFragment(fragment: Fragment, selectedId: Int = -1) {
        // Al cargar un fragmento principal de la navbar, ocultamos el botón de volver
        btnBack.visibility = View.GONE
        
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

    /**
     * Función pública para que los fragmentos puedan mostrar el botón de volver
     * cuando naveguen a una sub-pantalla.
     */
    fun showBackButton(show: Boolean) {
        btnBack.visibility = if (show) View.VISIBLE else View.GONE
    }
}
