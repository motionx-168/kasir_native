package com.digitalwhy.kasir

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey

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
}
