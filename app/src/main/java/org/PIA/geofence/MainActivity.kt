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


class MainActivity : AppCompatActivity() {

    private lateinit var navCuenta: LinearLayout
    private lateinit var navRutas: LinearLayout
    private lateinit var navHistorial: LinearLayout
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

        checkUserRole()

        navCuenta.setOnClickListener {
            loadFragment(CuentaFragment())
        }

        navHistorial.setOnClickListener {
            loadFragment(HistorialFragment())
        }
        navRutas.setOnClickListener {
            loadFragment(RutasFragment())
        }
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rol = document.getString("rol") ?: "sinRol"
                    if (rol == "sinRol") {
                        navbar.visibility = View.GONE
                        loadFragment(SinRolFragment())
                    } else {
                        navbar.visibility = View.VISIBLE
                        loadFragment(CuentaFragment())
                    }
                }
            }
            .addOnFailureListener {
                // En caso de error, por seguridad podemos mostrar la pantalla de sin rol
                navbar.visibility = View.GONE
                loadFragment(SinRolFragment())
            }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}