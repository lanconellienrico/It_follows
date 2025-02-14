package com.example.testapplication

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import java.sql.Time
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** [MCV pattern ]
 * The ActivityController is in charge of every action that can be performed
 * from the MainActivity Interface multiple times and is not strictly bound
 * to the activity creation or to its visual style, such as:
 * - start/stop an activity,
 * - save data on shared Preferences (using Model),
 * - schedule the reset of dailySteps counter,
 * - schedule the sending of a periodic Notification.
 *
 * Steps are handled differently between an ongoing activity and the dailySteps Count,
 * that's because dailySteps are reset to 0 at midnight, while an activity can last over it.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class ActivityController(private val model: ActivityModel, private val view: MainActivity) {

    private var areStepsInvolved: Boolean = false                   // current activity is step-able
    private lateinit var currentActivity: ActivityEntity            // activity currently ongoing


    // start a new activity, keep trace if it's a step-able activity, save it in the model and show Toast
    fun startActivity(activityType: String) {
        areStepsInvolved = (activityType == "walking" || activityType == "running")
        currentActivity = ActivityEntity(activityType = activityType)
        model.saveCurrentActivity(activityType, currentActivity.startTime)
        view.showToast("Thou art $activityType")
    }

    // stop current activity, make a toast and clear current activity
    fun stopActivity(dao: ActivityDao) {
        currentActivity.stopActivity(dao)
        areStepsInvolved = false
        model.clearCurrentActivity()
        view.showToast("Art thou tired of ${currentActivity.activityType}?")
    }

    // add new steps to daily count, save the new total and update view
    // IF CURRENT ACTIVITY IS STEP-ABLE -> add new steps to current activity and save on the model
    fun addNewSteps(newSteps: Int) {
        if(areStepsInvolved){
            currentActivity.steps += newSteps
            model.saveCurrentActivitySteps(currentActivity.steps)
        }
        val totalSteps = model.getSavedSteps() + newSteps
        model.saveSteps(totalSteps)
        view.updateStepCountView(totalSteps)
    }

    // save start/stop_activity Switch State on SharedPreferences
    fun saveSwitchState(state: Boolean) {
        model.saveSwitchState(state)
    }

    // restore previous states of Switch, Spinner and dailySteps on View, restore -if there- the ongoing activity
    fun getStatesBack() {
        view.updateStepCountView(model.getSavedSteps())
        view.setSwitchState(model.getSwitchState())

        val savedActivityType = model.getCurrentActivity().first   // savedActivity<type, startTime>
        val savedActivityStartTime = model.getCurrentActivity().second
        if(savedActivityType != null){
            currentActivity = ActivityEntity(activityType = savedActivityType, startTime = savedActivityStartTime)
            areStepsInvolved = (savedActivityType == "walking" || savedActivityType == "running")
            // it the activity is step-able, retrieve previous steps
            if(areStepsInvolved) {
                currentActivity.steps = model.getCurrentActivitySteps()
            }
            view.setSpinnerActivity(savedActivityType)
        }
    }

    // creating the request to reset daily steps and assigning it to the ResetDailyStepsWorker
    fun scheduleDailyStepsResetWorker(context: Context) {

        // setup periodic reset work
        val rightNow = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
        val midnight = rightNow.clone() as Calendar
        midnight.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)    // set the next midnight
        }
        val timeToMidnight = midnight.timeInMillis - rightNow.timeInMillis

        // create periodic work
        val dailyStepsWorkRequest = PeriodicWorkRequestBuilder<ResetDailyStepsWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(timeToMidnight, TimeUnit.MILLISECONDS)
            .build()

        // schedule work
        ResetDailyStepsWorker.addUniqueWork(context, dailyStepsWorkRequest)

        // TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST
        /*val dailyStepsWorkRequest = PeriodicWorkRequestBuilder<ResetDailyStepsWorker>(10, TimeUnit.SECONDS)
            .setInitialDelay(20000, TimeUnit.MILLISECONDS)
            .build()*/
        // TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST
    }

    // set a weekly notification for every Monday at 5:00 am
    // with the daily steps summary taken over the last week
    fun scheduleWeeklyStepsSummaryNotification(context: Context){
        // setup periodic notification sending -> next Monday at 5:00 am
        val rightNow = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
        val nextMonday = rightNow.clone() as Calendar
        nextMonday.apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // monday past 5 first exe case
            if (before(rightNow)){
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        var timeToMonday = nextMonday.timeInMillis - rightNow.timeInMillis

        // if timeToMonday < 24h, calc. time to the next Monday and update timeToMonday
        if (timeToMonday < 86400*1000) {
            nextMonday.add(Calendar.WEEK_OF_YEAR, 1)
            timeToMonday = nextMonday.timeInMillis - rightNow.timeInMillis
        }

        // EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST
        val isThisATest = false
        // EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST

        if(!isThisATest){
            val weeklyWorkRequest = PeriodicWorkRequestBuilder<WeeklyNotificationWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(timeToMonday, TimeUnit.MILLISECONDS)
                .build()

            // schedule work
            WeeklyNotificationWorker.addUniqueWork(context, weeklyWorkRequest)
        }
        // TEST
        else{
            val weeklyWorkRequest = PeriodicWorkRequestBuilder<WeeklyNotificationWorker>(30, TimeUnit.SECONDS).build()
            WeeklyNotificationWorker.addUniqueWork(context, weeklyWorkRequest)
        }
    }
}
