package com.example.testapplication

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Joining Link between SetGeoActivity and ViewGeoActivity.
 *
 * This singleton object is responsible for handling the geofence-related tasks:
 * - addGeofence (add a new geofence to the geo.Client and store it into Db)
 * - removeAllGeofences (remove all geofences from the geo.Client and delete them all from Db)

 * GeofencingClient and PendingIntent are initialized once and only once,
 * ensuring that both actions of adding and removing are done correctly
 * and avoiding duplication issues.
 * */
object GeofenceManager {

    private var geofencingClient: GeofencingClient? = null
    private var pendingIntent: PendingIntent? = null

    // initialize the geo.Client - only if it's not born already
    fun initialize(context: Context){
        if(geofencingClient == null)
            geofencingClient = LocationServices.getGeofencingClient(context)
    }

    /* create Pending Intent -only if it's the firstborn- with:
    * FLAG_UPDATE_CURRENT -> if that Pen.Int. already exists, update it with the new Intent
    * FLAG_IMMUTABLE -> this Pen.Int cannot be modified after its creation                  */
    private fun getGeofencePendingIntent(context: Context): PendingIntent{
        if (pendingIntent == null) {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return pendingIntent!!
    }

    // Add a geofence to the geo.Client and insert it into Db
    @SuppressLint("MissingPermission") // permissions are carefully checked already before this call
    fun addGeofence(context: Context, request: GeofencingRequest, geofence: GeofenceEntity) {
        initialize(context)

        geofencingClient?.addGeofences(request, getGeofencePendingIntent(context))
            ?.addOnSuccessListener {
                CoroutineScope(Dispatchers.IO).launch{
                    geofence.save(Db.getDb(context).geofenceEntityDao())
                }
                Toast.makeText(context, "'${geofence.id}' is henceforth in keep", Toast.LENGTH_SHORT).show()
                Log.d("Geofence Manager", "Geofence successfully added!")
            }
            ?.addOnFailureListener { e ->
                Toast.makeText(context,"'${geofence.id}' was, alas, not added to thy domains", Toast.LENGTH_SHORT).show()
                Log.d("Geofence Manager", "Error ${e.message}, geofence not added")
            }
    }

    // Remove all geofences from the geo.Client and delete their registrations in the Db
    fun removeAllGeofences(context: Context){
        initialize(context)

        geofencingClient?.removeGeofences(getGeofencePendingIntent(context))
            ?.addOnCompleteListener {
                CoroutineScope(Dispatchers.IO).launch{
                    Db.getDb(context).geofenceEntityDao().deleteGeofences()
                }
                Toast.makeText(context, "The earth doth sigh with a breeze reborn", Toast.LENGTH_LONG).show()
                Log.d("Geofence Manager", "All geofences removed")
            }
            ?.addOnFailureListener { e ->
                Toast.makeText(context, "Even Homer sometimes nods", Toast.LENGTH_LONG).show()
                Log.d("Geofence Manager", "Error ${e.message}, removeAll operation failed")
            }
    }
}