package org.PIA.geofence

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import org.PIA.geofence.ui.cuenta.CuentaFragment
import org.PIA.geofence.ui.historial.HistorialFragment
import org.PIA.geofence.ui.rutas.RutasFragment


class MainActivity : AppCompatActivity() {

    private lateinit var navCuenta: LinearLayout
    private lateinit var navRutas: LinearLayout
    private lateinit var navHistorial: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navbar = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.navbar)

        navCuenta = navbar.findViewById(R.id.nav_cuenta)
        navRutas = navbar.findViewById(R.id.nav_rutas)
        navHistorial = navbar.findViewById(R.id.nav_historial)


        if (savedInstanceState == null) {
            loadFragment(CuentaFragment())
        }

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

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}