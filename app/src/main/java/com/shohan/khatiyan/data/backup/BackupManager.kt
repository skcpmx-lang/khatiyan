package com.shohan.khatiyan.data.backup

import android.net.Uri
import com.shohan.khatiyan.core.BnDates
import androidx.room.withTransaction
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.settings.SettingsRepository
import com.shohan.khatiyan.utilities.DataBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Real local backup/restore through Storage Access Framework (Phase 26).
 *
 * - export: one UTF-8 JSON file chosen by the user; nothing leaves the device.
 * - import: strict validation first (marker, version, referential integrity),
 *   then a single-transaction REPLACE so duplicates by primary key update
 *   rather than multiply. Corrupt files are rejected with a Bangla message.
 */
class BackupManager(
    private val db: KhatiyanDatabase,
    private val settings: SettingsRepository,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun buildBackup(): BackupFile = withContext(Dispatchers.IO) {
        val shop = db.shopDao()
        val loan = db.loanDao()
        val emi = db.emiDao()
        val person = db.personalDao()
        val cash = db.cashflowDao()
        val profile = db.profileDao().getProfile()
        BackupFile(
            exportedAtIso = java.time.LocalDateTime.now().toString(),
            userName = profile?.name ?: settings.current().userName,
            currencySymbol = profile?.currencySymbol ?: settings.current().currencySymbol,
            profile = profile,
            shops = shop.getAllShops(),
            shopCredits = shop.getAllCredits(),
            shopCreditItems = shop.getAllCreditItems(),
            shopPayments = shop.getAllPayments(),
            loans = loan.getAllLoans(),
            loanInstallments = loan.getAllInstallments(),
            loanPayments = loan.getAllPayments(),
            loanAllocations = loan.getAllAllocations(),
            emiPurchases = emi.getAllEmis(),
            emiInstallments = emi.getAllInstallments(),
            emiPayments = emi.getAllPayments(),
            emiAllocations = emi.getAllAllocations(),
            persons = person.getAllPeople(),
            personalDebts = person.getAllDebts(),
            personalRepayments = person.getAllRepayments(),
            incomes = cash.getAllIncomes(),
            expenses = cash.getAllExpenses(),
            customCategories = cash.getAllCategories(),
        )
    }

    suspend fun serialize(file: BackupFile): ByteArray =
        json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8)

    suspend fun exportTo(context: android.content.Context, uri: Uri): Int {
        val file = buildBackup()
        val bytes = serialize(file)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: throw BackupFormatException("ফাইলটি লেখা যায়নি — আবার চেষ্টা করুন।")
        }
        return file.totalRows()
    }

    fun parse(bytes: ByteArray): BackupFile {
        if (bytes.size < 16) throw BackupFormatException("ফাইলটি পড়া যায়নি — খালি বা নষ্ট ফাইল।")
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("এটি বৈধ খতিয়ান ব্যাকআপ ফাইল নয় (JSON নষ্ট হয়ে গেছে)।")
        }
        validate(file)
        return file
    }

    fun validate(file: BackupFile) {
        if (file.app != BackupFile.APP_MARKER) {
            throw BackupFormatException("ফাইলটি খতিয়ানের ব্যাকআপ নয়।")
        }
        if (file.backupVersion < BackupFile.MIN_SUPPORTED_VERSION ||
            file.backupVersion > BackupFile.CURRENT_VERSION
        ) {
            throw BackupFormatException(
                "ব্যাকআপ সংস্করণ ${com.shohan.khatiyan.utilities.BnText.toBnDigits(file.backupVersion.toString())} এই অ্যাপে সাপোর্টেড নয়।",
            )
        }
        if (file.shops.any { it.id <= 0 } || file.loans.any { it.id <= 0 } ||
            file.emiPurchases.any { it.id <= 0 } || file.persons.any { it.id <= 0 }
        ) {
            throw BackupFormatException("ব্যাকআপে অবৈধ রেকর্ড আইডি পাওয়া গেছে — ফাইলটি নষ্ট হতে পারে।")
        }
        val shopIds = file.shops.map { it.id }.toSet()
        val loanIds = file.loans.map { it.id }.toSet()
        val emiIds = file.emiPurchases.map { it.id }.toSet()
        val personIds = file.persons.map { it.id }.toSet()
        if (file.shopCredits.any { it.shopId !in shopIds } ||
            file.shopPayments.any { it.shopId !in shopIds } ||
            file.loans.any { it.id <= 0 } ||
            file.loanPayments.any { it.loanId !in loanIds } ||
            file.emiPayments.any { it.emiId !in emiIds } ||
            file.personalDebts.any { it.personId !in personIds }
        ) {
            throw BackupFormatException("ব্যাকআপ ফাইলে কিছু রেকর্ডের মূল এন্ট্রি নেই — ফাইলটি অসম্পূর্ণ।")
        }
        val creditIds = file.shopCredits.map { it.id }.toSet()
        if (file.shopCreditItems.any { it.creditId !in creditIds }) {
            throw BackupFormatException("ব্যাকআপ ফাইলে পণ্যের এন্ট্রি তার মূল বাকির সাথে মেলেনি।")
        }
        if (file.shops.any { it.name.isBlank() }) {
            throw BackupFormatException("দোকানের নাম ছাড়া রেকর্ড পাওয়া গেছে — ফাইলটি ঠিক নয়।")
        }
    }

    fun summarize(bytes: ByteArray): BackupSummary {
        val f = parse(bytes)
        return BackupSummary(
            backupVersion = f.backupVersion,
            exportedAtIso = f.exportedAtIso,
            userName = f.userName,
            shops = f.shops.size,
            loans = f.loans.size,
            emis = f.emiPurchases.size,
            persons = f.persons.size,
            incomes = f.incomes.size,
            expenses = f.expenses.size,
            totalRows = f.totalRows(),
        )
    }

    /** Full replace inside one transaction — atomic: any failure rolls back. */
    suspend fun restoreFrom(context: android.content.Context, uri: Uri): BackupSummary =
        withContext(Dispatchers.IO) {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw BackupFormatException("ফাইলটি খোলা যায়নি।")
            } catch (e: IOException) {
                throw BackupFormatException("ফাইলটি পড়া যায়নি — আবার চেষ্টা করুন।")
            }
            val file = parse(bytes)
            val summary = summarize(bytes)
            db.withTransaction {
                db.clearAllTables() // views are not tables — untouched
                file.profile?.let { db.profileDao().upsertProfile(it) }
                db.shopDao().restoreShops(file.shops)
                db.personalDao().restorePeople(file.persons)
                db.loanDao().restoreLoans(file.loans)
                db.emiDao().restoreEmis(file.emiPurchases)
                db.shopDao().restoreCredits(file.shopCredits)
                db.personalDao().restoreDebts(file.personalDebts)
                db.loanDao().restoreInstallments(file.loanInstallments)
                db.emiDao().restoreInstallments(file.emiInstallments)
                db.shopDao().restoreCreditItems(file.shopCreditItems)
                db.shopDao().restorePayments(file.shopPayments)
                db.loanDao().restorePayments(file.loanPayments)
                db.loanDao().restoreAllocations(file.loanAllocations)
                db.emiDao().restorePayments(file.emiPayments)
                db.emiDao().restoreAllocations(file.emiAllocations)
                db.personalDao().restoreRepayments(file.personalRepayments)
                db.cashflowDao().restoreIncomes(file.incomes)
                db.cashflowDao().restoreExpenses(file.expenses)
                db.cashflowDao().restoreCategories(file.customCategories)
            }
            DataBus.poke()
            summary
        }

    fun suggestFileName(): String = "khatiyan-backup-${BnDates.toIso(BnDates.today())}.json"
}

fun BackupFile.totalRows(): Int =
    shops.size + shopCredits.size + shopCreditItems.size + shopPayments.size +
        loans.size + loanInstallments.size + loanPayments.size + loanAllocations.size +
        emiPurchases.size + emiInstallments.size + emiPayments.size + emiAllocations.size +
        persons.size + personalDebts.size + personalRepayments.size +
        incomes.size + expenses.size + customCategories.size
