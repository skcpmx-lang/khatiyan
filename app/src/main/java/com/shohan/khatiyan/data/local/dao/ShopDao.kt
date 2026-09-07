package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shohan.khatiyan.data.local.entity.ShopCreditEntity
import com.shohan.khatiyan.data.local.entity.ShopCreditItemEntity
import com.shohan.khatiyan.data.local.entity.ShopEntity
import com.shohan.khatiyan.data.local.entity.ShopPaymentEntity
import com.shohan.khatiyan.data.local.query.ShopRow
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShopDao {

    @Query(
        """
        SELECT s.id, s.name, s.ownerName, s.phone, s.address, s.category, s.note, s.archived,
               (SELECT IFNULL(SUM(c.totalPaisa), 0) FROM shop_credits c WHERE c.shopId = s.id) AS totalCreditPaisa,
               (SELECT IFNULL(SUM(p.amountPaisa), 0) FROM shop_payments p WHERE p.shopId = s.id) AS totalPaidPaisa,
               (SELECT MAX(c.dateIso) FROM shop_credits c WHERE c.shopId = s.id) AS lastCreditIso
        FROM shops s
        WHERE (:hideArchived = 0 OR s.archived = 0)
          AND (:q = '' OR s.name LIKE '%' || :q || '%' OR s.ownerName LIKE '%' || :q || '%' OR s.category LIKE '%' || :q || '%')
        ORDER BY s.archived ASC, s.updatedAtIso DESC, s.id DESC
        """
    )
    abstract fun observeShopRows(q: String, hideArchived: Boolean): Flow<List<ShopRow>>

    @Query("SELECT * FROM shops WHERE id = :id")
    abstract suspend fun getShop(id: Long): ShopEntity?

    @Query("SELECT id, name FROM shops WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    abstract suspend fun getActiveShopRefs(): List<com.shohan.khatiyan.data.local.query.ShopRefRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertShop(shop: ShopEntity): Long

    @Delete
    abstract suspend fun deleteShop(shop: ShopEntity)

    @Query("UPDATE shops SET archived = :archived, updatedAtIso = :nowIso WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean, nowIso: String)

    // ---- credit entries ----------------------------------------------------------------

    @Insert
    abstract suspend fun insertCredit(credit: ShopCreditEntity): Long

    @Update
    abstract suspend fun updateCredit(credit: ShopCreditEntity)

    @Insert
    abstract suspend fun insertItems(items: List<ShopCreditItemEntity>)

    @Query("DELETE FROM shop_credit_items WHERE creditId = :creditId")
    abstract suspend fun deleteItemsFor(creditId: Long)

    /** Items are replaced wholesale on edit — the pair (credit, items) is one document. */
    @Transaction
    open suspend fun saveCredit(
        credit: ShopCreditEntity,
        items: List<ShopCreditItemEntity>,
    ): Long {
        val creditId: Long
        if (credit.id == 0L) {
            creditId = insertCredit(credit)
        } else {
            updateCredit(credit)
            deleteItemsFor(credit.id)
            creditId = credit.id
        }
        insertItems(items.map { it.copy(id = 0, creditId = creditId) })
        return creditId
    }

    @Query("DELETE FROM shop_credits WHERE id = :id")
    abstract suspend fun deleteCredit(id: Long)

    @Query("SELECT * FROM shop_credits WHERE id = :id")
    abstract suspend fun getCredit(id: Long): ShopCreditEntity?

    @Query("SELECT * FROM shop_credits WHERE shopId = :shopId ORDER BY dateIso DESC, id DESC")
    abstract suspend fun getCredits(shopId: Long): List<ShopCreditEntity>

    @Query("SELECT * FROM shop_credit_items WHERE creditId = :creditId ORDER BY sortOrder ASC, id ASC")
    abstract suspend fun getItems(creditId: Long): List<ShopCreditItemEntity>

    @Query(
        """
        SELECT i.* FROM shop_credit_items i
        JOIN shop_credits c ON c.id = i.creditId
        WHERE c.shopId = :shopId
        ORDER BY c.dateIso DESC, c.id DESC, i.sortOrder ASC
        """
    )
    abstract suspend fun getItemsForShop(shopId: Long): List<ShopCreditItemEntity>

    // ---- payments ----------------------------------------------------------------------

    @Insert
    abstract suspend fun insertPayment(payment: ShopPaymentEntity): Long

    @Update
    abstract suspend fun updatePayment(payment: ShopPaymentEntity)

    @Query("DELETE FROM shop_payments WHERE id = :id")
    abstract suspend fun deletePayment(id: Long)

    @Query("SELECT * FROM shop_payments WHERE id = :id")
    abstract suspend fun getPayment(id: Long): ShopPaymentEntity?

    @Query("SELECT * FROM shop_payments WHERE shopId = :shopId ORDER BY dateIso DESC, id DESC")
    abstract suspend fun getPayments(shopId: Long): List<ShopPaymentEntity>

    @Query(
        """
        SELECT s.id, s.name, s.ownerName, s.phone, s.address, s.category, s.note, s.archived,
               (SELECT IFNULL(SUM(c.totalPaisa), 0) FROM shop_credits c WHERE c.shopId = s.id) AS totalCreditPaisa,
               (SELECT IFNULL(SUM(p.amountPaisa), 0) FROM shop_payments p WHERE p.shopId = s.id) AS totalPaidPaisa,
               (SELECT MAX(c.dateIso) FROM shop_credits c WHERE c.shopId = s.id) AS lastCreditIso
        FROM shops s ORDER BY s.name COLLATE NOCASE
        """
    )
    abstract suspend fun getShopRows(): List<ShopRow>

    @Query("SELECT id, name FROM shops ORDER BY name COLLATE NOCASE")
    abstract suspend fun getAllShopRefs(): List<com.shohan.khatiyan.data.local.query.ShopRefRow>

    // ---- aggregates (dashboard / reports / backup restore) ----------------------------

    @Query("SELECT * FROM shop_credits")
    abstract suspend fun getAllCredits(): List<ShopCreditEntity>

    @Query("SELECT * FROM shop_credit_items")
    abstract suspend fun getAllCreditItems(): List<ShopCreditItemEntity>

    @Query("SELECT * FROM shop_payments")
    abstract suspend fun getAllPayments(): List<ShopPaymentEntity>

    @Query("SELECT * FROM shops")
    abstract suspend fun getAllShops(): List<ShopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreShops(shops: List<ShopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreCredits(credits: List<ShopCreditEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreCreditItems(items: List<ShopCreditItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restorePayments(payments: List<ShopPaymentEntity>)
}
