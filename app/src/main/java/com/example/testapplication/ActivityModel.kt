package com.example.testapplication

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.ZoneId

/** [ MCV pattern ]
 * The ActivityModel manage the access (for both writing and reading actions)
 * to the sharedPreferences Resources, relieving the MainActivity from the task.
 *
 * Here are saved:
 * - daily step's count
 * - start|stop_activity switch's state
 * - type, startTime (and -if there- steps) of the ongoing activity
 * - dailyStepsEntity date of the recording day
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

    // create dailyStepsEntity, Insert Entity into Db, reset count to 0, update currentDay and stepReset
    // *** called on midnight by the ResetDailyStepsWorker ***
    fun saveTodaySteps(context: Context){
        // insert into Db
        val dao = Db.getDb(context).dailyStepsEntityDao()
        DailyStepsEntity(date = getDay(), steps = getSavedSteps()).save(dao)

        // reset steps, and update stepReset and current day
        saveSteps(0)
        setStepReset(true)
        newDay()
    }

    // is step's reset happened? yes -> true | no -> false
    fun getStepReset(): Boolean{
        return sharedPreferences.getBoolean("stepReset", false)
    }

    // stepReset is set TRUE when a reset's happened
    // when the MainActivity View is update, then become FALSE
    fun setStepReset(resetHasHappened: Boolean){
        sharedPreferences.edit().putBoolean("stepReset", resetHasHappened).apply()
    }

    // get the state of start/stop_activity Switch
    fun getSwitchState(): Boolean {
        return sharedPreferences.getBoolean("startStopActivitySwitchState", false)
    }

    // save the state of start/stop_activity Switch
    fun saveSwitchState(state: Boolean) {
        sharedPreferences.edit().putBoolean("startStopActivitySwitchState", state).apply()
    }

    // get type and start-time of ongoing activity
    fun getCurrentActivity(): Pair<String?, Long>{
        val type = sharedPreferences.getString("currentActivityType", null)
        val startTime = sharedPreferences.getLong("currentActivityStartTime", 0L)
        return Pair(type, startTime)
    }

    // save type and start-time on ongoing activity
    fun saveCurrentActivity(type: String, startTime: Long){
        sharedPreferences.edit()
            .putString("currentActivityType", type)
            .putLong("currentActivityStartTime", startTime)
            .apply()
    }

    // activity is over -> remove type, startTime and - if there - steps
    fun clearCurrentActivity(){
        sharedPreferences.edit()
            .remove("currentActivityType")
            .remove("currentActivityStartTime")
            .remove("currentActivitySteps")
            .apply()
    }

    // get the number of steps taken of the ongoing activity
    fun getCurrentActivitySteps(): Int{
        return sharedPreferences.getInt("currentActivitySteps", 0)
    }

    // steps count has changed -> update number of steps of ongoing activity
    fun saveCurrentActivitySteps(steps: Int){
        sharedPreferences.edit().putInt("currentActivitySteps", steps).apply()
    }

    // a new day is come: update currentDay!
    private fun newDay() {
        currentDay += 1
    }

    private fun getDay(): Long{
        return currentDay
    }
}