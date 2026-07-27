package com.impulsive.app.backend.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.impulsive.app.backend.data.local.entity.CloudRestoreReceiptEntity

@Dao
interface CloudRestoreReceiptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(receipt: CloudRestoreReceiptEntity)

    @Query(
        "SELECT * FROM cloud_restore_receipts " +
            "ORDER BY importedAtMillis DESC LIMIT 1",
    )
    suspend fun latest(): CloudRestoreReceiptEntity?

    @Query(
        "SELECT * FROM cloud_restore_receipts " +
            "WHERE receiptId = :receiptId LIMIT 1",
    )
    suspend fun find(receiptId: String): CloudRestoreReceiptEntity?

    @Query(
        "DELETE FROM cloud_restore_receipts WHERE receiptId = :receiptId",
    )
    suspend fun delete(receiptId: String): Int

    @Query("DELETE FROM cloud_restore_receipts")
    suspend fun clearAll()
}
