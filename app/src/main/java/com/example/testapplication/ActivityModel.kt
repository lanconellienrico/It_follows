package com.example.testapplication

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.ZoneId

/** [ MCV pattern ]
 * The ActivityModel manage the access (for both writing and reading actions)
 * to the sharedPreferences Resources, relieving the MainActivity from the task
 */
@RequiresApi(Build.VERSION_CODES.O)
class ActivityModel(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    private var currentDay: Long = LocalDate.now(ZoneId.of("Europe/Paris")).toEpochDay()

    // get daily steps count back
    fun getSavedSteps(): Int {
        return sharedPreferences.getInt("stepCount", 0)
    }

    // save input 'steps' as daily steps count
    fun saveSteps(steps: Int) {
        sharedPreferences.edit().putInt("stepCount", steps).apply()
        Log.d("ActivityModel", "saveSteps($steps)")
    }

    // get the state of start/stop_activity Switch
    fun getSwitchState(): Boolean {
        return sharedPreferences.getBoolean("startStopActivitySwitchState", false)
    }

    // save the state of start/stop_activity Switch
    fun saveSwitchState(state: Boolean) {
        sharedPreferences.edit().putBoolean("startStopActivitySwitchState", state).apply()
    }

    // a new day is come: update currentDay!
    fun newDay() {
        currentDay += 1
    }

    fun getDay(): Long{
        return currentDay
    }
}