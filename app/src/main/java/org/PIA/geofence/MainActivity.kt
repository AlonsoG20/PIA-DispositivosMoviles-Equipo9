package org.PIA.geofence

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
        headerLayout = findViewById(R.id.header)
        btnBack = headerLayout.findViewById(R.id.btn_back)
        tvUserName = headerLayout.findViewById(R.id.tv_user_name)
        tvUserRole = headerLayout.findViewById(R.id.tv_user_role)
        
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                
                // 1. Manejo interno de fragmentos (ej. cerrar sub-menú de gestión)
                if (currentFragment is GestionFragment && currentFragment.onBackPressed()) return
                
                // 2. Manejo de pila (ej. volver de Gestión-Personal a Reportes)
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }
                
                // 3. Comportamiento por defecto
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        // Sincronización automática del Navbar basada en el Tag del Fragmento actual
        supportFragmentManager.addOnBackStackChangedListener {
            syncNavbarSelection()
        }

        initNavViews()
        observeUser()
        checkUserRole()
    }

    private fun initNavViews() {
        navCuenta = navbar.findViewById(R.id.nav_cuenta)
        navRutas = navbar.findViewById(R.id.nav_rutas)
        navHistorial = navbar.findViewById(R.id.nav_historial)
        navGestion = navbar.findViewById(R.id.nav_gestion)
        navReportes = navbar.findViewById(R.id.nav_reportes)

        navCuenta.setOnClickListener { loadFragment(CuentaFragment(), it.id) }
        navHistorial.setOnClickListener { loadFragment(HistorialFragment(), it.id) }
        navRutas.setOnClickListener {
            val user = userViewModel.userData.value
            val fragment = if (user?.rol == "despachador") RutasDespachadorFragment() else RutasFragment()
            loadFragment(fragment, it.id)
        }
        navGestion.setOnClickListener { 
            val user = userViewModel.userData.value
            val fragment = if (user?.rol == "despachador") ControlDespachadorFragment() else GestionFragment()
            loadFragment(fragment, it.id)
        }
        navReportes.setOnClickListener { loadFragment(ReportesFragment(), it.id) }
    }

    private fun loadFragment(fragment: Fragment, selectedId: Int) {
        // Al cambiar de pestaña principal, limpiamos la historia de sub-navegación
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, selectedId.toString()) // Usamos el ID como Tag
            .commit()
        
        updateNavSelection(selectedId)
    }

    private fun syncNavbarSelection() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val tagId = fragment?.tag?.toIntOrNull() ?: -1
        if (tagId != -1) {
            updateNavSelection(tagId)
        }
        // Mostrar botón atrás solo si hay algo en la pila
        btnBack.visibility = if (supportFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
    }

    private fun updateNavSelection(selectedId: Int) {
        navCuenta.isSelected = (selectedId == R.id.nav_cuenta)
        navRutas.isSelected = (selectedId == R.id.nav_rutas)
        navHistorial.isSelected = (selectedId == R.id.nav_historial)
        navGestion.isSelected = (selectedId == R.id.nav_gestion)
        navReportes.isSelected = (selectedId == R.id.nav_reportes)
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
                loadFragment(SinRolFragment(), -1)
            }
        }
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        userViewModel.loadUser(userId)
    }

    private fun setupUIByRole(rol: String) {
        navbar.visibility = View.VISIBLE
        navCuenta.visibility = View.VISIBLE
        navRutas.visibility = View.GONE
        navHistorial.visibility = View.GONE
        navGestion.visibility = View.GONE
        navReportes.visibility = View.GONE

        when (rol) {
            "gerente" -> {
                headerLayout.setBackgroundColor(Color.parseColor("#CE8CF5"))
                navbar.findViewById<TextView>(R.id.tvNavGestion).text = "Gestión"
                navGestion.visibility = View.VISIBLE
                navReportes.visibility = View.VISIBLE
                if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    loadFragment(GestionFragment(), R.id.nav_gestion)
                }
            }
            "despachador" -> {
                headerLayout.setBackgroundColor(Color.parseColor("#FAB24B"))
                navbar.findViewById<TextView>(R.id.tvNavGestion).text = "Control"
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
                loadFragment(SinRolFragment(), -1)
            }
        }
    }

    fun showBackButton(show: Boolean) {
        btnBack.visibility = if (show || supportFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
    }
}
