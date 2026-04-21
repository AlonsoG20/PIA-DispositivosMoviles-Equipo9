package org.PIA.geofence.ui.gestion

import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import org.PIA.geofence.R
import java.io.IOException
import java.util.Locale

class AddPoiActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var etSearch: EditText
    private lateinit var etName: EditText
    private var selectedMarker: Marker? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_poi)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        etSearch = findViewById(R.id.etSearchPOI)
        etName = findViewById(R.id.etNamePOI)
        val btnSearch = findViewById<ImageButton>(R.id.btnSearchPOI)
        val btnSave = findViewById<Button>(R.id.btnSavePOI)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapPOI) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnSearch.setOnClickListener {
            val location = etSearch.text.toString()
            if (location.isNotEmpty()) {
                searchLocation(location)
            }
        }

        btnSave.setOnClickListener {
            savePoi()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // Posición inicial (Monterrey)
        val defaultLatLng = LatLng(25.6866, -100.3161)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 12f))

        // Escuchar clics en el mapa para colocar el marcador
        mMap.setOnMapClickListener { latLng ->
            updateSelectedLocation(latLng)
        }
    }

    private fun updateSelectedLocation(latLng: LatLng) {
        selectedMarker?.remove()
        selectedMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Punto seleccionado")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun searchLocation(location: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(location, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        runOnUiThread {
                            updateSelectedLocation(latLng)
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@AddPoiActivity, "No se encontró el lugar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                override fun onError(errorMessage: String?) {
                    runOnUiThread {
                        Toast.makeText(this@AddPoiActivity, "Error al buscar: $errorMessage", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } else {
            try {
                @Suppress("DEPRECATION")
                val addressList: List<Address>? = geocoder.getFromLocationName(location, 1)
                if (addressList != null && addressList.isNotEmpty()) {
                    val address = addressList[0]
                    val latLng = LatLng(address.latitude, address.longitude)
                    updateSelectedLocation(latLng)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                } else {
                    Toast.makeText(this, "No se encontró el lugar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error de red al buscar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePoi() {
        val name = etName.text.toString().trim()
        val markerPos = selectedMarker?.position

        if (name.isEmpty()) {
            Toast.makeText(this, "Ingresa un nombre para el punto", Toast.LENGTH_SHORT).show()
            return
        }

        if (markerPos == null) {
            Toast.makeText(this, "Por favor selecciona un punto en el mapa", Toast.LENGTH_SHORT).show()
            return
        }

        val poi = hashMapOf(
            "nombre" to name,
            "lugar" to GeoPoint(markerPos.latitude, markerPos.longitude)
        )

        db.collection("puntosInteres")
            .add(poi)
            .addOnSuccessListener {
                Toast.makeText(this, "Punto de interés guardado", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
