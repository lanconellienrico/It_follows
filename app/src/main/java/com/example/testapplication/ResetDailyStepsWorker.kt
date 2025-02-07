package com.example.testapplication

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * ResetDailyStepsWorker is in charge of the periodic task
 * of resetting to zero the count of daily steps every midnight.
 * Only one reset-work can exist, so it must be unique!
 *
 * The work assignation is handled in the ActivityController -> scheduleDailyStepsResetWorker()
 */
@RequiresApi(Build.VERSION_CODES.O)
class ResetDailyStepsWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {

    override fun doWork(): Result {

        // reset daily steps count
        ActivityModel(applicationContext).saveTodaySteps(applicationContext)
        Log.d("ResetDailyStepsWorker", "Daily Steps count successful reset. ")
        return Result.success()
    }

    companion object{
        private const val TAG = "ResetDailyStepsWorker"
        private const val WORK = "reset_daily_steps"

        fun addUniqueWork(context: Context, workRequest: PeriodicWorkRequest){
            val workManager = WorkManager.getInstance(context)

            // check if a worker for the job has already been scheduled
            val workInfo = workManager.getWorkInfosForUniqueWork(WORK).get()
            if (workInfo.isNotEmpty() && workInfo.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING}) {
                Log.d(TAG, "Worker's already active, you lazy ass")
                return
            }

            // add the work to the queue, with .KEEP policy -> we don't want duplicate
            workManager.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, workRequest
            )
            Log.d(TAG, "A new $TAG has been scheduled successfully")
        }
    }
}