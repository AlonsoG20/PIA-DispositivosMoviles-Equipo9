package org.PIA.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class ReceptorGeofence : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) return

        // Verificamos que sea una entrada a la zona
        if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            geofencingEvent.triggeringGeofences?.forEach { geofence ->
                Toast.makeText(context,
                    "Llegaste a: ${geofence.requestId}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
}