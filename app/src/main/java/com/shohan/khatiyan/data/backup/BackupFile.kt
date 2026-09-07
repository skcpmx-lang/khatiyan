package com.shohan.khatiyan.data.backup

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
import com.shohan.khatiyan.data.local.entity.PersonEntity
import com.shohan.khatiyan.data.local.entity.PersonalDebtEntity
import com.shohan.khatiyan.data.local.entity.PersonalRepaymentEntity
import com.shohan.khatiyan.data.local.entity.ShopCreditEntity
import com.shohan.khatiyan.data.local.entity.ShopCreditItemEntity
import com.shohan.khatiyan.data.local.entity.ShopEntity
import com.shohan.khatiyan.data.local.entity.ShopPaymentEntity
import com.shohan.khatiyan.data.local.entity.UserProfileEntity
import kotlinx.serialization.Serializable

/**
 * On-device backup file schema (Phase 26). Everything the user typed, in one
 * JSON document — no server ever sees it. Entities are reused directly, so a
 * backup round-trip is loss-free.
 */
@Serializable
data class BackupFile(
    val backupVersion: Int = BackupFile.CURRENT_VERSION,
    val app: String = BackupFile.APP_MARKER,
    val exportedAtIso: String = "",
    val userName: String = "",
    val currencySymbol: String = "৳",
    val profile: UserProfileEntity? = null,
    val shops: List<ShopEntity> = emptyList(),
    val shopCredits: List<ShopCreditEntity> = emptyList(),
    val shopCreditItems: List<ShopCreditItemEntity> = emptyList(),
    val shopPayments: List<ShopPaymentEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val loanInstallments: List<LoanInstallmentEntity> = emptyList(),
    val loanPayments: List<LoanPaymentEntity> = emptyList(),
    val loanAllocations: List<LoanAllocationEntity> = emptyList(),
    val emiPurchases: List<EmiPurchaseEntity> = emptyList(),
    val emiInstallments: List<EmiInstallmentEntity> = emptyList(),
    val emiPayments: List<EmiPaymentEntity> = emptyList(),
    val emiAllocations: List<EmiAllocationEntity> = emptyList(),
    val persons: List<PersonEntity> = emptyList(),
    val personalDebts: List<PersonalDebtEntity> = emptyList(),
    val personalRepayments: List<PersonalRepaymentEntity> = emptyList(),
    val incomes: List<IncomeEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val customCategories: List<CategoryEntity> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val MIN_SUPPORTED_VERSION = 1
        const val APP_MARKER = "khatiyan-backup"
    }
}

/** Human-readable pre-restore preview. */
data class BackupSummary(
    val backupVersion: Int,
    val exportedAtIso: String,
    val userName: String,
    val shops: Int,
    val loans: Int,
    val emis: Int,
    val persons: Int,
    val incomes: Int,
    val expenses: Int,
    val totalRows: Int,
)

class BackupFormatException(val messageBn: String) : Exception(messageBn)
