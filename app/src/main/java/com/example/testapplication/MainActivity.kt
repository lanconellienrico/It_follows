package com.example.testapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room.databaseBuilder
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import java.util.Calendar
import java.util.TimeZone

/**
 * MainActivity of the App, handles onCreate-related events, one-time works and graphic elements.
 * It delegates periodic actions and utility tasks (graphic_unrelated) to an ActivityController,
 * while the access to the sharedPreferences-resources is managed by an ActivityModel.
 * [MVC Pattern Adopted]
 *
 * in prose:
 * Hero of the journey, the centre of our story,
 * but like every troubled soul, it needs friends that come in aid
 * and so MainActivity, God! such an appealing name, handles only unique events and graphic views
 * while it wisely relies on its friends 'the Controller' and 'the Model' for the most tedious tasks.
 */

@RequiresApi(Build.VERSION_CODES.Q)
class MainActivity:AppCompatActivity(), SensorEventListener {

    // Model & Controller
    private lateinit var model: ActivityModel
    private lateinit var controller: ActivityController

    //graphic elements
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var startStopActivitySwitch: Switch
    private lateinit var historyButton: Button
    private lateinit var chartsButton: Button
    private lateinit var setGeofenceButton: Button
    private lateinit var viewGeofenceButton: Button
    private lateinit var addStepsButton: Button
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var activityTypeSpinner: Spinner
    private lateinit var stepCountTextView: TextView

    // utility stuff
    private lateinit var db: Db
    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var lastSensorStepCount = 0                   // last number of steps rec. by the sensor
    private var newSensorStepCount = 0                    // new number of steps rec. by the sensor
    private val ACTIVITY_RECOGNITION_REQUEST_CODE = 1001
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 2
    private val POST_NOTIFICATION_PERMISSION_REQUEST_CODE = 3


    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.default_activity_create)

        model = ActivityModel(this)                                             //Model
        controller = ActivityController(model,this)                               //Controller
        db = databaseBuilder(applicationContext, Db::class.java, "Db").build()    //set up Db

        setupUI()                // set up graphic elements
        checkPermissions()       // check if everything's in order with permissions

        // MIDNIGHT WORKER -> setup a worker for resetting daily steps count at midnight
        controller.scheduleDailyStepsResetWorker(this)

        // WEEKLY NOTIFICATION -> setup a worker for sending the Weekly Steps Summary as Notification
        controller.scheduleWeeklyStepsSummaryNotification(this)

        // SPINNER -> configuration of the spinner menu for selecting activity
        val adapter = ArrayAdapter.createFromResource(this, R.array.activities, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // SENSOR configuration for steps counting, carefully managing if it is not available
        stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounterSensor == null)
            showToast( "Step counter sensor not available!")

        // BUTTON LISTENERS -> add listeners to buttons
        addStepsButton.setOnClickListener { dialogAddSteps() }
        historyButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, HistoryActivity::class.java))
        }
        chartsButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, ChartsActivity::class.java))
        }
        setGeofenceButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, SetGeoActivity::class.java))
        }
        viewGeofenceButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, ViewGeoActivity::class.java))
        }

        /* SWITCH LISTENER - resume start/stop_activity Switch state by setting the listener null,
           then set the Switch:
           - if there is no activity going on  -> switchState OFF
           - if there's an activity going on   -> switchState ON        */
        startStopActivitySwitch.setOnCheckedChangeListener(null)
        controller.getStatesBack()

        // set listener on the start/stop_activity switch every time changes its state( ON = true, OFF = false)
        startStopActivitySwitch.setOnCheckedChangeListener { _, isOn ->
            if (isOn) {          //ON
                controller.startActivity(activityTypeSpinner.selectedItem.toString())
                controller.saveSwitchState(true)
            } else {              //OFF
                controller.stopActivity(db.activityEntityDao())
                controller.saveSwitchState(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        //onResume retrieve saved dailySteps count
        val previousSteps = model.getSavedSteps().toString()
        stepCountTextView.text = previousSteps
    }

    // UTILITY METHODS still graphic related *****************************************************************************

    // associate local vars to layout elements
    private fun setupUI(){
        activityTypeSpinner = findViewById(R.id.select_activity_spinner)
        stepCountTextView = findViewById(R.id.n_steps)
        historyButton = findViewById(R.id.history_button)
        chartsButton = findViewById(R.id.charts_button)
        setGeofenceButton = findViewById(R.id.geofence_set_button)
        viewGeofenceButton = findViewById(R.id.geofence_cross_button)
        addStepsButton = findViewById(R.id.add_steps_button)
        startStopActivitySwitch = findViewById(R.id.start_stop_switch)
    }

    // display Toast on screen with the given String
    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // update view of dailySteps-Counter
    fun updateStepCountView(steps: Int) {
        val stepsToString = steps.toString()
        stepCountTextView.text = stepsToString
    }

    // set start/stop_activity-Switch State
    fun setSwitchState(state: Boolean) {
        startStopActivitySwitch.isChecked = state
    }

    // create a dialog window to add daily steps happened out of the app
    private fun dialogAddSteps(){
        // Set up the input
        val inputSteps = EditText(this)
        inputSteps.inputType = InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Add unto thine daily steps")
            .setMessage("How many thou you declare?")
            .setView(inputSteps)
            .setPositiveButton("add") { _, _ ->
                val newSteps = inputSteps.text.toString().toIntOrNull() ?: 0

                // add new steps and make a Toast
                controller.addNewSteps(newSteps)
                showToast("Added $newSteps to thine steps!")
            }
            .setNegativeButton("Back", null)
            .create()
            .show()
    }
    // ***************************************************************************************************************************


    // PERMISSION METHODS to make sure we got the papers done ************************************************************************************************

    //Method to check permission demands results
    private fun checkPermissions() {
        Log.d("PermissionCheck", "Checking permissions...")

        // check authorization for ACTIVITY RECOGNITION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), ACTIVITY_RECOGNITION_REQUEST_CODE)
        }

        // check authorization for ACCESS FINE LOCATION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
        }

        // check permission for POST NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPostNotificationPermission()
            }
        }
    }

    // Method to handle needed authorizations
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            ACTIVITY_RECOGNITION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i("MainActivity", "Activity Recognition permission granted!")
                } else {
                    showToast("Thou hast denied the permission! Activity cannot be recognized")
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i("MainActivity", "Fine Location permission granted!")

                    // now ask for background location permission
                    if (ContextCompat.checkSelfPermission(
                            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestBackgroundLocationPermission()
                    }
                } else {
                    showToast("Thou hast denied the permission! Location cannot be retrieved")
                }
            }
            POST_NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i("MainActivity", "Notifications permission granted!")
                } else{
                    showToast("Notifications permission hath not been granted! Dreadful omens loom ahead")
                }
            }
        }
    }

    // ask for fine location permission
    private fun requestLocationPermission() {
        AlertDialog.Builder(this)
            .setTitle("They are demanding")
            .setMessage("Art thou disposed to unveil thy standing?")
            .setPositiveButton("Indeed") { _, _ ->
                ActivityCompat.requestPermissions(this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)}
            .setNegativeButton("Deny", null)
            .create()
            .show()
    }

    // ask for background location permission
    private fun requestBackgroundLocationPermission() {
        AlertDialog.Builder(this)
            .setTitle("They are demanding")
            .setMessage("Wilt thou grant them sight of thy standing, even with thy back turned?")
            .setPositiveButton("Indeed") { _, _ ->
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("Deny", null)
            .create()
            .show()
    }

    // ask for post notifications permission
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestPostNotificationPermission(){
        AlertDialog.Builder(this)
            .setTitle("An offer thou canst not spurn")
            .setMessage("Wilt thou hearken to their whispers?")
            .setPositiveButton("Indeed") { _, _ ->
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    POST_NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
            .create()
            .show()
    }
    // *******************************************************************************************************************

    // STEP COUNTER SENSOR METHODS ***************************************************************************************

    // on sensor event, update number of steps in the view and save it on the model
    // event.values[0].toInt() once Sensor is ON, keeps count of the total N of steps rec. by the it
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            newSensorStepCount = event.values[0].toInt()
            var deltaSteps = newSensorStepCount

            // if last rec. != 0 than calc. DELTA, then assign newValue to lastValue
            if(lastSensorStepCount != 0){
                deltaSteps = newSensorStepCount - lastSensorStepCount
            }
            lastSensorStepCount = newSensorStepCount

            // add delta steps to total steps count and update view
            controller.addNewSteps(deltaSteps)
        }
    }

    // here nobody is exclude, but that does not mean it can be useful
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }
    // ******************************************************************************************************************
}