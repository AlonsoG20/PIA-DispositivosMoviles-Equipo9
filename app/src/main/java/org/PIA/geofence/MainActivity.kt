package org.PIA.geofence

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
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
import org.PIA.geofence.ui.cuenta.UserViewModel


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
    private lateinit var headerLayout: ConstraintLayout

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userViewModel: UserViewModel by viewModels()

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
        headerLayout = findViewById(R.id.header)
        btnBack = headerLayout.findViewById(R.id.btn_back)
        tvUserName = headerLayout.findViewById(R.id.tv_user_name)
        tvUserRole = headerLayout.findViewById(R.id.tv_user_role)
        
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Registrar callback para manejar el gesto de atrás del sistema
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                
                // Manejar retroceso en GestionFragment
                if (currentFragment is GestionFragment) {
                    if (currentFragment.onBackPressed()) return
                }
                
                // Manejar retroceso en RutasDespachadorFragment (Asignación de rutas)
                if (currentFragment is RutasDespachadorFragment) {
                    if (currentFragment.onBackPressed()) return
                }
                
                // Si no hay nada que manejar internamente, permitimos el comportamiento por defecto (salir)
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        navCuenta = navbar.findViewById(R.id.nav_cuenta)
        navRutas = navbar.findViewById(R.id.nav_rutas)
        navHistorial = navbar.findViewById(R.id.nav_historial)
        navGestion = navbar.findViewById(R.id.nav_gestion)
        navReportes = navbar.findViewById(R.id.nav_reportes)

        observeUser()
        checkUserRole()

        navCuenta.setOnClickListener { loadFragment(CuentaFragment(), it.id) }
        navHistorial.setOnClickListener { loadFragment(HistorialFragment(), it.id) }
        navRutas.setOnClickListener {
            val user = userViewModel.userData.value
            if (user != null) {
                if (user.rol == "despachador") {
                    loadFragment(RutasDespachadorFragment(), it.id)
                } else {
                    loadFragment(RutasFragment(), it.id)
                }
            } else {
                // Fallback if not loaded
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
        }
        navGestion.setOnClickListener { 
            val user = userViewModel.userData.value
            if (user != null) {
                if (user.rol == "despachador") {
                    loadFragment(ControlDespachadorFragment(), it.id)
                } else {
                    loadFragment(GestionFragment(), it.id)
                }
            } else {
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
        }
        navReportes.setOnClickListener { loadFragment(ReportesFragment(), it.id) }
    }

    private fun observeUser() {
        userViewModel.userData.observe(this) { user ->
            if (user != null) {
                tvUserName.text = user.nombreCompleto
                tvUserRole.text = user.rol?.replaceFirstChar { it.uppercase() } ?: "Usuario"
                setupUIByRole(user.rol ?: "sinRol")
            }
        }
        
        userViewModel.error.observe(this) { error ->
            if (error != null) {
                navbar.visibility = View.GONE
                loadFragment(SinRolFragment())
            }
        }
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        userViewModel.loadUser(userId)
    }

    private fun setupUIByRole(rol: String) {
        navbar.visibility = View.VISIBLE
        
        // Resetear visibilidades
        navCuenta.visibility = View.VISIBLE
        navRutas.visibility = View.GONE
        navHistorial.visibility = View.GONE
        navGestion.visibility = View.GONE
        navReportes.visibility = View.GONE

        // Cambiar color de fondo del header según el rol
        when (rol) {
            "gerente" -> {
                headerLayout.setBackgroundColor(Color.parseColor("#CE8CF5"))
                
                val tvGestion = navbar.findViewById<TextView>(R.id.tvNavGestion)
                tvGestion.text = "Gestión"
                navGestion.visibility = View.VISIBLE
                navHistorial.visibility = View.GONE // SE QUITA LA PESTAÑA PARA MOVERLA DENTRO DE GESTIÓN
                navReportes.visibility = View.VISIBLE
                if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    loadFragment(GestionFragment(), R.id.nav_gestion)
                }
            }
            "despachador" -> {
                headerLayout.setBackgroundColor(Color.parseColor("#FAB24B"))
                
                val tvGestion = navbar.findViewById<TextView>(R.id.tvNavGestion)
                tvGestion.text = "Control"
                navGestion.visibility = View.VISIBLE
                navRutas.visibility = View.VISIBLE
                navHistorial.visibility = View.VISIBLE
                if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    loadFragment(ControlDespachadorFragment(), R.id.nav_gestion)
                }
            }
            "chofer" -> {
                headerLayout.setBackgroundColor(Color.parseColor("#789D9C"))
                
                navRutas.visibility = View.VISIBLE
                navHistorial.visibility = View.VISIBLE
                if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    loadFragment(RutasFragment(), R.id.nav_rutas)
                }
            }
            else -> {
                headerLayout.setBackgroundColor(Color.parseColor("#789D9C"))
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
