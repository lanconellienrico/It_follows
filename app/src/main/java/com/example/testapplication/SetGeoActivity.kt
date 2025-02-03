package com.example.testapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * SetGeoActivity: provide a form to create a Geofence by setting its
 * - Name > works as Id in the Db, with REPLACE policy,
 * - Latitude and Longitude > can be either filled out with custom par. or by using the current location,
 * - Radius (meter) > for best results, the min. radius should be set between 100 - 150 meters.
 *
 * There's also the checkable option to overwrite a geofence with the same name.
 *
 * The trigger event is not set as ENTER|EXIT, but as DWELL( 30s loiter), so that alert spam is reduced.
 * The notification responsiveness is set at 5min in order to save power.
 *
 * Once the Geofence is added to the geofencingClient, the GeofenceBroadcastReceiver is in the charge
 * of intercepting possible crossings within the geofence.
 */
class SetGeoActivity: AppCompatActivity() {

    //graphic elements
    private lateinit var inputName: TextView
    private lateinit var inputLatitude: TextView
    private lateinit var inputLongitude: TextView
    private lateinit var inputRadius: TextView
    private lateinit var saveGeofenceButton: Button
    private lateinit var useCurrentLocationButton: Button
    private lateinit var geofenceOverwrite: CheckBox

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.set_geo_activity)

        setupUI()

        // initialize geofence service manager
        GeofenceManager.initialize(this)

        // BUTTON LISTENERS
        useCurrentLocationButton.setOnClickListener {
            fillWithCurrentLocation()
        }
        saveGeofenceButton.setOnClickListener {
            saveGeofence()
        }
    }

    // link vars to their relative layout elements
    private fun setupUI() {
        inputName = findViewById(R.id.set_geo_name)
        inputLatitude = findViewById(R.id.set_latitude)
        inputLongitude = findViewById(R.id.set_longitude)
        inputRadius = findViewById(R.id.set_radius)
        useCurrentLocationButton = findViewById(R.id.use_current_position_button)
        saveGeofenceButton = findViewById(R.id.save_geo_button)
        geofenceOverwrite = findViewById(R.id.view_geo_overwrite_checkbox)
    }

    // fill latitude and longitude fields with current location values
    @SuppressLint("SetTextI18n")
    private fun fillWithCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Thou had denied the permission! Location cannot be retrieved", Toast.LENGTH_SHORT).show()
            return
        }

        // if location access is allowed -> get current location and set on latitude|longitude TextView
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if(location != null){
                    inputLatitude.text = location.latitude.toString()
                    inputLongitude.text = location.longitude.toString()
                } else{
                    Toast.makeText(this, "Alas! No location could be found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "The heavens hath forsaken thee! Unable to retrieve location.", Toast.LENGTH_LONG).show()
            }
    }

    // check if every input text is filled and correct, if then pass to building
    private fun saveGeofence() {
        // get the inputs
        val name = inputName.text
        val latitude = inputLatitude.text
        val longitude = inputLongitude.text
        val radius = inputRadius.text

        // check if every field has been filled out(1) and with valid values(2)
        var emptyFields = ""
        var wrongInput = ""
        if(name.isEmpty()) {
            emptyFields += "- NAME"
            showErrorText(inputName, "empty")
            // name's validity is check later
        }
        if(latitude.isEmpty()) {
            emptyFields += "- LAT"
            showErrorText(inputLatitude, "empty")
        } else if(latitude.toString().toDoubleOrNull() == null) {
            wrongInput += "LATITUDE "
            showErrorText(inputLatitude, "wrong")
        } else if(latitude.toString().toDouble() < -90 || latitude.toString().toDouble() > 90){
            wrongInput += "LATITUDE "
            showErrorText(inputLatitude, "lat")
        }
        if(longitude.isEmpty()) {
            emptyFields += "- LONG"
            showErrorText(inputLongitude, "empty")
        } else if(longitude.toString().toDoubleOrNull() == null) {
            wrongInput += "LONGITUDE "
            showErrorText(inputLongitude, "wrong")
        } else if(longitude.toString().toDouble() < -180 || longitude.toString().toDouble() > 180){
            wrongInput += "LONGITUDE "
            showErrorText(inputLongitude, "long")
        }
        if(radius.isEmpty()) {
            emptyFields += "- RAD"
            showErrorText(inputRadius, "empty")
        } else if(radius.toString().toFloatOrNull() == null) {
            wrongInput += "RADIUS"
            showErrorText(inputRadius, "wrong")
        }

        // compose a toast message to tell possible errors
        var toastMessage = ""
        if(emptyFields.isNotEmpty()){
            toastMessage = "Those scrolls await thy hand, yet untouched: $emptyFields\n"
        }
        if(wrongInput.isNotEmpty()){
            toastMessage += "Thy signs require more care: $wrongInput"
        }

        // if the toast_msg is yet null -> There are no errors, fields are filled and correct
        if(toastMessage == ""){
            toastMessage = buildGeofence(
                name.toString(),
                latitude.toString().toDouble(),
                longitude.toString().toDouble(),
                radius.toString().toFloat()
            ).toString()
        }
        // if buildGeofence() has generated text, display it in the Toast
        if(toastMessage != "")
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
    }

    // show precise error message in the field (empty field error | wrong value error)
    private fun showErrorText(place: TextView, errorType: String){
        when (errorType){
            "empty" -> place.error = "Thou must yet write upon me!"
            "wrong" -> place.error = "Beware! Thy inscription is errant"
            "long" -> place.error = "Longitude is bound between -180 and 180"
            "lat" -> place.error = "Latitude is confined within -90 and 90"
        }
    }

    // create everything needed for the geofence building: request, intent, pendingIntent
    // then add geofence to the location service and in case of success -> insert into DB
    private fun buildGeofence(name: String, lat: Double, long: Double, rad: Float): String?{
        // build the geofence
        val geofence = Geofence.Builder()
            .setRequestId(name)
            .setCircularRegion(lat, long, rad)
            .setNotificationResponsiveness(300000) // check for crossings every 5min
            .setLoiteringDelay(30000) // 30s needed to trigger the TRANSITION_DWELL
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
            .build()

        // build the request
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        // check if permissions are granted, if then proceed, otherwise return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "It seemeth thou hast not granted the required permissions"
        }

        // returns a string in case of a geofence with the same name already stored and the will to not overwrite it
        // otherwise the Toast will be directly created by the outcome_listeners on the addGeofences operation (async)x
        return runBlocking {
            val dao = Db.getDb(applicationContext).geofenceEntityDao()

            // check if the name is already to another splendid geofence creature
            val isThereAlready = withContext(Dispatchers.IO){
                dao.getGeofenceByName(name)?.isNotEmpty()?:false
            }

            /* if a geofence with the name is already stored, read the overwrite option checkbox
             * checked -> proceed with geofence creation and insert into DB
             * not checked -> keep the already stored geofence and return                      */
            if(isThereAlready) {
                if(!geofenceOverwrite.isChecked)
                    return@runBlocking " Verily, '${name}' is a realm already in thy grasp"
            }

            // Delegate to the Manager : adding geofence to the geo.client and insert into Db
            GeofenceManager.addGeofence(this@SetGeoActivity, request, GeofenceEntity(name, lat, long, rad))

            return@runBlocking null
        }
    }
}