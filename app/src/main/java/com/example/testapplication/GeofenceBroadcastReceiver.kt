package com.example.testapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GeofenceBroadcastReceiver catches every Geofence crossing and increment the count on the Db.
 *
 * heartwarming line:
 * GeofencyBrodRec acts as a Champion for every father indeed,
 *             catching every ball thrown with spring and joy,
 *                                     such a pride for daddy.
 */
class GeofenceBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(context == null || intent == null) return

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent != null) {
            if(geofencingEvent.hasError()) {
                Log.e("GeofenceBroadcastReceiver", "Houston, got a  ${geofencingEvent.errorCode} problem over here, at Geofencing Event")
                return
            }
            val transitionType = geofencingEvent.geofenceTransition
            if(transitionType == Geofence.GEOFENCE_TRANSITION_DWELL){
                val geofenceName = geofencingEvent.triggeringGeofences?.firstOrNull()?.requestId ?: return

                // increment geofence crosses on Db
                CoroutineScope(Dispatchers.IO).launch{
                    Db.getDb(context).geofenceEntityDao().addCrossToGeofenceByName(geofenceName)
                }

                Toast.makeText(context, "Thou art now one within $geofenceName", Toast.LENGTH_LONG).show()
            }
        }
    }
}