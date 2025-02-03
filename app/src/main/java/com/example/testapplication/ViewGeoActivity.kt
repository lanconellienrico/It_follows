package com.example.testapplication

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewGeoActivity: shows every Geofence registered with the number of crossings made,
 * it also provides two buttons:
 * "add new Geofence" -> which brings directly into the SetGeoActivity,
 * "delete every Geofence Button" -> that lives up to its name and erase every Geofence in the Db
 *
 */
class ViewGeoActivity: AppCompatActivity() {

    //graphic elements
    private lateinit var sampleText: TextView
    private lateinit var noGeofenceText: TextView
    private lateinit var geofencesContainer: LinearLayout
    private lateinit var addGeofenceButton: Button
    private lateinit var deleteGeofenceButton: Button

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_geo_activity)

        setupUI()
        getGeofences()

        // BUTTON LISTENERS
        addGeofenceButton.setOnClickListener{
            startActivity(Intent(this@ViewGeoActivity, SetGeoActivity::class.java))
        }
        deleteGeofenceButton.setOnClickListener{
            deleteGeofences()
        }
    }

    override fun onResume() {
        super.onResume()
        getGeofences()
    }

    // link local vars to layout elements
    private fun setupUI(){
        sampleText = findViewById(R.id.view_geo_crosses_sample)
        noGeofenceText = findViewById(R.id.view_geo_none_yet)
        geofencesContainer = findViewById(R.id.crosses_container)
        addGeofenceButton = findViewById(R.id.to_set_geo_button)
        deleteGeofenceButton = findViewById(R.id.delete_geo_button)
    }

    // fetch geofences from Db and add to view
    private fun getGeofences(){
        CoroutineScope(Dispatchers.IO).launch{
            val geofences = Db.getDb(applicationContext).geofenceEntityDao().getGeofences()
            withContext(Dispatchers.Main) {
                if(geofences.isEmpty()) {
                    noGeofenceText.visibility = View.VISIBLE
                } else{
                    noGeofenceText.visibility = View.GONE
                    geofencesContainer.removeAllViewsInLayout()
                    geofences.forEach { geo ->
                        addGeofenceTextView(geo.toPrint())
                    }
                }
            }
        }
    }

    // show geofences' crosses in style
    private fun addGeofenceTextView(geo: String){
        // copy paste of sampleStyle
        val newTextView = TextView(ContextThemeWrapper(
            this, R.style.geofenceTitle)).apply {
            this.text = geo
        }
        newTextView.visibility = View.VISIBLE

        // set layout_MarginBottom here cause Android still has a long way to go
        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(0, 16, 0, 0)
        newTextView.layoutParams = layoutParams

        // add textView to the Layout Container
        geofencesContainer.addView(newTextView)
    }

    // delete every geofence from Db
    private fun deleteGeofences(){
        AlertDialog.Builder(this)
            .setTitle("To forsake for good")
            .setMessage("Art thou resolved to unmake thy bounds?")
            .setPositiveButton("Indeed") { _, _ ->
                GeofenceManager.removeAllGeofences(this)
                geofencesContainer.removeAllViewsInLayout()
                noGeofenceText.visibility = View.VISIBLE
            }
            .setNegativeButton("Deny") {_, _ ->
                Toast.makeText(this, "Thy wealth bindeth thee in unseen chains", Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }
}