package com.digitalwhy.kasir

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val totalAmount: Double,
    val itemsSummary: String,
    val isSynced: Boolean = false
)

// Pindahkan interface TransactionDao ke sini (seperti aslinya)
@androidx.room.Dao
interface TransactionDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @androidx.room.Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<Transaction>

    @androidx.room.Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Int)
    
    @androidx.room.Query("SELECT * FROM transactions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Transaction>
    
    @androidx.room.Update
    suspend fun update(transaction: Transaction)
}
