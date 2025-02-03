package com.example.testapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * WeeklyNotificationWorker is in charge of this periodic Notification:
 * send to the user a summary of the steps taken over the last week,
 * in comparison with what was achieved the week before.
 * The task has to be performed every Monday at 5:00 am.
 * Only one Weekly-Summary-Steps-Notificator-Worker can exist, so it must be unique!
 *
 * The work assignation is handled in the ActivityController -> scheduleWeeklyStepsSummaryNotification()
 *
 * TESTING > in order to check if it does what it says:
 * - set isThisATest = true (here and on the controller method)
 * - run the App
 * - set isThisATest = false ONLY in the Controller
 * - run the App
 * - set isThisATest = false FINALLY HERE TOO
 */
@RequiresApi(Build.VERSION_CODES.O)
class WeeklyNotificationWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {

    override fun doWork(): Result {
        startNotificationProcess()
        return Result.success()
    }

    companion object{
        private const val TAG = "WeeklyNotificationWorker"
        private const val WORK = "weekly_daily_steps_summary"

        fun addUniqueWork(context: Context, workRequest: PeriodicWorkRequest){
            val workManager = WorkManager.getInstance(context)

            // EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST
            val isThisATest = false
            // EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST  EDIT TO TEST

            if(!isThisATest){
                // check if a worker for the job has already been scheduled
                val workInfo = workManager.getWorkInfosForUniqueWork(WORK).get()
                if (workInfo.isNotEmpty() && workInfo.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING}) {
                    Log.d(TAG, "Worker's already active, you noisy twat")
                    return
                }
                // add the work to the queue, with .KEEP policy -> do not create it if there's one already
                workManager.enqueueUniquePeriodicWork(
                    WORK,
                    ExistingPeriodicWorkPolicy.KEEP, // EDIT TO UPDATE TO TEST - EDIT TO UPDATE TO TEST - EDIT TO UPDATE TO TEST
                    workRequest
                )
                Log.d(TAG, "A new $TAG has been scheduled successfully")
            }
            // TEST
            else{
                // add the work to the queue, with .UPDATE policy -> replace one if there already
                workManager.enqueueUniquePeriodicWork(
                    WORK,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
                Log.d(TAG, "A new $TAG has been scheduled with great success")
            }
        }
    }

    /* start the process of building the notification by fetching dailySteps entries from Db,
        -> get last week and the week before data from Db and calc. the two means ***
       *** if there are enough DailySteps entries                                                */
    private fun startNotificationProcess(){
        val dao = Db.getDb(applicationContext).dailyStepsEntityDao()

        // fetch last week and the week before DailySteps from Db, then calc. means
        CoroutineScope(Dispatchers.IO).launch {
            val recordedDailySteps = dao.getLastNDaysDailySteps(14)

            // if there are no entries, both Means keep 0
            var beforeWeekMean = 0
            var lastWeekMean = 0
            if(recordedDailySteps.isNotEmpty()){
                // take at most the first 7 entries ('.take' -> avoid crash if they're less)
                val lastWeekSteps = recordedDailySteps.take(7)
                lastWeekMean = lastWeekSteps.map { it.steps }.average().toInt()

                // if there are 14 entries, take the next seven e get Mean of the week before
                if(recordedDailySteps.size == 14){
                    val beforeWeekSteps = recordedDailySteps.subList(7, 14)
                    beforeWeekMean = beforeWeekSteps.map { it.steps}.average().toInt()
                }
            }
            // pass the Means to the Notification-Text Maker
            makeNotificationText(lastWeekMean, beforeWeekMean)
        }
    }

    // once lastWeek and theWeekBefore means are calc., generate the appropriate text for the Notification
    private fun makeNotificationText(lastWeekMean: Int, beforeWeekMean: Int){
        val finalText: String
        when{
            (lastWeekMean == 0) -> finalText = "Thy reign is yet to come"
            (beforeWeekMean == 0) -> finalText = "Thy daily steps hath averaged $lastWeekMean these past seven days!"
            (lastWeekMean < beforeWeekMean) -> {
                val difference = beforeWeekMean-lastWeekMean
                finalText = "The steps thou hast taken this week are but shadows" +
                        " of the mighty glories of yore, lacking $difference in number"
            }
            else -> { // more than the week before
                val difference = lastWeekMean - beforeWeekMean
                finalText = "Thy weekly deeds, impressive indeed, hath outshone " +
                        "the yet remarkable strides of the past, outstripping them by the count of $difference"
            }
        }
        sendNotification(finalText)
    }

    // create notification channel, set intent, build notification and finally notify
    private fun sendNotification(contentText: String){

        val context = applicationContext
        val notificationName = "Weekly Daily Steps Summary"
        val channelID = "it_follows_weekly_notification_channel"
        val notificationManager = NotificationManagerCompat.from(context)
        val existingChannel = notificationManager.getNotificationChannel(channelID)

        // if notification permission's not been granted, do not make the notification
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return }

        // create notification channel if it's not already there
        if(existingChannel == null){
            val channel = NotificationChannel(
                channelID,
                notificationName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply{
                description = notificationName
            }
            notificationManager.createNotificationChannel(channel)
        }

        // set Intent -> open app on click on the notification
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("caller", notificationName)
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // notification building
        val builder = NotificationCompat.Builder(context, channelID)
            .setSmallIcon(R.drawable.heart)
            .setContentTitle(notificationName)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(1, builder.build())
    }
}