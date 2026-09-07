package com.shohan.khatiyan.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Self-chaining daily reminder (WorkManager) — scheduled to the user-chosen
 * hour, reschedules itself after each run. Unique name + REPLACE guarantees a
 * single pending reminder regardless of how many times settings are touched.
 */
object ReminderScheduler {

    private const val WORK_NAME = "khatiyan-daily-reminder"

    fun nextDelaySeconds(hour: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var target = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), 0))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).seconds.coerceAtLeast(60L)
    }

    fun schedule(context: Context, hour: Int) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(nextDelaySeconds(hour), java.util.concurrent.TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
