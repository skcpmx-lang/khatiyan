package com.shohan.khatiyan.di

import android.app.Application
import com.shohan.khatiyan.data.backup.BackupManager
import com.shohan.khatiyan.data.backup.PdfExporter
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.repository.CashflowRepository
import com.shohan.khatiyan.data.repository.DashboardRepository
import com.shohan.khatiyan.data.repository.DueRepository
import com.shohan.khatiyan.data.repository.EmiRepository
import com.shohan.khatiyan.data.repository.LoanRepository
import com.shohan.khatiyan.data.repository.PersonalRepository
import com.shohan.khatiyan.data.repository.ReportRepository
import com.shohan.khatiyan.data.repository.SearchRepository
import com.shohan.khatiyan.data.repository.ShopRepository
import com.shohan.khatiyan.data.settings.SettingsRepository
import com.shohan.khatiyan.notification.NotificationHelper
import com.shohan.khatiyan.notification.ReminderScheduler
import com.shohan.khatiyan.security.LockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container — one instance per process, lazy singletons.
 * Deliberately dependency-light (no DI framework) which keeps the build fast
 * and the object graph auditable for an app this size.
 */
class AppContainer(private val app: Application) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val db: KhatiyanDatabase by lazy { KhatiyanDatabase.build(app) }

    val shopRepo: ShopRepository by lazy { ShopRepository(db) }
    val loanRepo: LoanRepository by lazy { LoanRepository(db) }
    val emiRepo: EmiRepository by lazy { EmiRepository(db) }
    val personalRepo: PersonalRepository by lazy { PersonalRepository(db) }
    val cashflowRepo: CashflowRepository by lazy { CashflowRepository(db) }
    val dueRepo: DueRepository by lazy { DueRepository(db) }
    val dashboardRepo: DashboardRepository by lazy { DashboardRepository(db, settings, dueRepo) }
    val reportRepo: ReportRepository by lazy { ReportRepository(db, settings, dueRepo) }
    val searchRepo: SearchRepository by lazy { SearchRepository(db) }
    val backupManager: BackupManager by lazy { BackupManager(db, settings) }
    val pdfExporter: PdfExporter by lazy { PdfExporter(app) }
    val appLock: LockManager by lazy { LockManager(settings) }

    fun onAppCreate() {
        NotificationHelper.ensureChannel(app)
        applicationScope.launch {
            val s = settings.current()
            if (s.onboarded && s.notificationsEnabled) {
                ReminderScheduler.schedule(app, s.reminderHour)
            }
        }
    }

    fun notificationsTurned(on: Boolean) {
        applicationScope.launch {
            if (on) {
                ReminderScheduler.schedule(app, settings.current().reminderHour)
            } else {
                ReminderScheduler.cancel(app)
                db.notificationStateDao().clear()
            }
        }
    }
}
