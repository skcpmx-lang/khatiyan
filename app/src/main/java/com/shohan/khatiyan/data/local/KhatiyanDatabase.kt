package com.shohan.khatiyan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shohan.khatiyan.data.local.dao.CashflowDao
import com.shohan.khatiyan.data.local.dao.EmiDao
import com.shohan.khatiyan.data.local.dao.GlobalSearchDao
import com.shohan.khatiyan.data.local.dao.LedgerDao
import com.shohan.khatiyan.data.local.dao.LoanDao
import com.shohan.khatiyan.data.local.dao.NotificationStateDao
import com.shohan.khatiyan.data.local.dao.PersonalDao
import com.shohan.khatiyan.data.local.dao.ProfileDao
import com.shohan.khatiyan.data.local.dao.ShopDao
import com.shohan.khatiyan.data.local.dao.StatsDao
import com.shohan.khatiyan.data.local.entity.CategoryEntity
import com.shohan.khatiyan.data.local.entity.EmiAllocationEntity
import com.shohan.khatiyan.data.local.entity.EmiInstallmentEntity
import com.shohan.khatiyan.data.local.entity.EmiPaymentEntity
import com.shohan.khatiyan.data.local.entity.EmiPurchaseEntity
import com.shohan.khatiyan.data.local.entity.ExpenseEntity
import com.shohan.khatiyan.data.local.entity.IncomeEntity
import com.shohan.khatiyan.data.local.entity.LoanAllocationEntity
import com.shohan.khatiyan.data.local.entity.LoanEntity
import com.shohan.khatiyan.data.local.entity.LoanInstallmentEntity
import com.shohan.khatiyan.data.local.entity.LoanPaymentEntity
import com.shohan.khatiyan.data.local.entity.NotificationStateEntity
import com.shohan.khatiyan.data.local.entity.PersonEntity
import com.shohan.khatiyan.data.local.entity.PersonalDebtEntity
import com.shohan.khatiyan.data.local.entity.PersonalRepaymentEntity
import com.shohan.khatiyan.data.local.entity.ShopCreditEntity
import com.shohan.khatiyan.data.local.entity.ShopCreditItemEntity
import com.shohan.khatiyan.data.local.entity.ShopEntity
import com.shohan.khatiyan.data.local.entity.ShopPaymentEntity
import com.shohan.khatiyan.data.local.entity.UserProfileEntity

/**
 * খতিয়ান database (Phase 30). Single source of truth on-device.
 *
 * Version 1. Schema JSON is exported to app/schemas via KSP; future releases
 * MUST add formal Room migrations (no destructive fallback, so accidental
 * data loss can never happen silently). Foreign keys use CASCADE to make
 * orphan records impossible; balance edits/deletes run inside @Transaction.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        ShopEntity::class,
        ShopCreditEntity::class,
        ShopCreditItemEntity::class,
        ShopPaymentEntity::class,
        LoanEntity::class,
        LoanInstallmentEntity::class,
        LoanPaymentEntity::class,
        LoanAllocationEntity::class,
        EmiPurchaseEntity::class,
        EmiInstallmentEntity::class,
        EmiPaymentEntity::class,
        EmiAllocationEntity::class,
        PersonEntity::class,
        PersonalDebtEntity::class,
        PersonalRepaymentEntity::class,
        IncomeEntity::class,
        ExpenseEntity::class,
        CategoryEntity::class,
        NotificationStateEntity::class,
    ],
    views = [LedgerRowView::class],
    version = 1,
    exportSchema = true,
)
abstract class KhatiyanDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao
    abstract fun loanDao(): LoanDao
    abstract fun emiDao(): EmiDao
    abstract fun personalDao(): PersonalDao
    abstract fun cashflowDao(): CashflowDao
    abstract fun statsDao(): StatsDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun globalSearchDao(): GlobalSearchDao
    abstract fun profileDao(): ProfileDao
    abstract fun notificationStateDao(): NotificationStateDao

    companion object {
        const val NAME = "khatiyan.db"

        fun build(context: Context): KhatiyanDatabase =
            Room.databaseBuilder(context.applicationContext, KhatiyanDatabase::class.java, NAME)
                .build()

        fun buildInMemory(context: Context): KhatiyanDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, KhatiyanDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
