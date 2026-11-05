package com.digitalwhy.kasir

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String,
    val barcode: String,
    val name: String,
    val price: Double,
    val stock: Double,
    val costPrice: Double = 0.0,
    val expiryDate: String? = null,
    val type: String = "barcode",
    val icon: String = "package"
)
