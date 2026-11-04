package com.digitalwhy.kasir

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val totalAmount: Double,
    val itemsSummary: String
)

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp < :end")
    suspend fun getTransactionsBetween(start: Long, end: Long): List<Transaction>
}
