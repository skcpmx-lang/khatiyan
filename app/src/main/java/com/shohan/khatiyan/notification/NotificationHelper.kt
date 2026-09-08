package com.shohan.khatiyan.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shohan.khatiyan.MainActivity
import com.shohan.khatiyan.R
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money

/**
 * Local notifications only (Phase 20) — WorkManager driven, no push services.
 * A single stable notification id replaces the previous content, so the user
 * never gets duplicates for the same day; the lock-screen preview deliberately
 * shows no financial details.
 */
object NotificationHelper {

    const val CHANNEL_ID = "khatiyan_reminders"
    const val NOTIFICATION_ID = 20260101

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name) + " — পেমেন্ট স্মরণ",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "প্রতিদিন বকেয়া আর আগামীকালের পেমেন্টের স্মরণ"
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showPaymentDigest(context: Context, overdueCount: Int, overduePaisa: Long, dueTodayCount: Int, dueTodayPaisa: Long, tomorrowCount: Int, symbol: String) {
        val parts = ArrayList<String>()
        if (dueTodayCount > 0) parts += "আজ ${dueTodayCount}টি পেমেন্ট দিতে হবে (${Money.format(dueTodayPaisa, symbol)})"
        if (overdueCount > 0) parts += "সময় পার হওয়া ${overdueCount}টি পেমেন্ট (${Money.format(overduePaisa, symbol)})"
        if (tomorrowCount > 0) parts += "আগামীকাল ${tomorrowCount}টি পেমেন্ট আসন্ন"
        if (parts.isEmpty()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "khatiyan.OPEN_DASHBOARD"
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Lock-screen safe public version: no amounts at all.
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_khatiyan)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("একবার দেখে নিন")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_khatiyan)
            .setContentTitle("পেমেন্টের হিসাব — ${BnDates.formatLong(BnDates.today())}")
            .setContentText(parts.joinToString(" · "))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "খতিয়ান — পেমেন্ট স্মরণ\n\n" + parts.joinToString("\n") { "• $it" },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // permission revoked since settings check — skip silently (Phase 36)
        }
    }
}
