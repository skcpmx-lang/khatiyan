package com.shohan.khatiyan.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shohan.khatiyan.KhatiyanApp
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.data.local.entity.NotificationStateEntity
import com.shohan.khatiyan.domain.model.DueStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Builds the daily digest from LIVE database data and posts it once per day
 * (dedupe via the notification_state table keyed by day). Never runs when
 * notifications are off; always reschedules the next slot before returning.
 */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KhatiyanApp
        val container = app?.container
        if (container == null) {
            // App not initialised (should not happen) — retry shortly.
            return Result.retry()
        }
        try {
            val settings = container.settings.current()
            if (settings.onboarded && settings.notificationsEnabled) {
                if (NotificationHelper.canPost(applicationContext)) {
                    runCatching {
                        val today = BnDates.today()
                        val stateDao = container.db.notificationStateDao()
                        val key = "digest:$today"
                        if (stateDao.lastNotifiedIso(key) != today.toString()) {
                            val items = withContext(Dispatchers.Default) {
                                container.dueRepo.collect(today, 1)
                            }
                            val overdue = items.filter { it.status == DueStatus.OVERDUE }
                            val dueToday = items.filter { it.status == DueStatus.DUE_TODAY }
                            val tomorrow = items.filter { it.dueDate == today.plusDays(1) }
                            val hasAnything = overdue.isNotEmpty() || dueToday.isNotEmpty()
                            if (hasAnything) {
                                NotificationHelper.showPaymentDigest(
                                    context = applicationContext,
                                    overdueCount = overdue.size,
                                    overduePaisa = overdue.sumOf { it.amountPaisa },
                                    dueTodayCount = dueToday.size,
                                    dueTodayPaisa = dueToday.sumOf { it.amountPaisa },
                                    tomorrowCount = tomorrow.size,
                                    symbol = settings.currencySymbol,
                                )
                                stateDao.mark(NotificationStateEntity(key, today.toString()))
                            }
                        }
                    }
                }
                ReminderScheduler.schedule(applicationContext, settings.reminderHour)
            }
        } catch (e: Exception) {
            // never crash the host; retry later so the chain survives
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        fun rescheduleAfterBootIfNeeded(context: Context) {
            // WorkManager re-schedules pending unique work on its own after
            // reboot; this hook exists so KhatiyanApp has one clear entry.
        }
    }
}

private fun LocalDate.iso(): String = this.toString()
