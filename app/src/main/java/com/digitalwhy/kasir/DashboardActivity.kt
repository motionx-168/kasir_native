package com.digitalwhy.kasir

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        database = AppDatabase.getInstance(this)

        findViewById<MaterialCardView>(R.id.btnKasir).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnManageProducts).setOnClickListener {
            Toast.makeText(this, "Segera Hadir", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            Toast.makeText(this, "Segera Hadir", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
                .format(Calendar.getInstance().time)

        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) { calculateSummary() }

            findViewById<TextView>(R.id.tvRevenue).text = formatCurrency(summary.revenue)
            findViewById<TextView>(R.id.tvProfit).text = formatCurrency(summary.profit)
            findViewById<TextView>(R.id.tvLowStock).text = summary.lowStock.toString()
            findViewById<TextView>(R.id.tvExpired).text = summary.expiring.toString()
        }
    }

    private suspend fun calculateSummary(): DashboardSummary {
        val products = database.productDao().getAllProducts()

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = dayStart.timeInMillis
        val endMillis = (dayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val transactions =
            database.transactionDao().getTransactionsBetween(startMillis, endMillis)

        val revenue = transactions.sumOf { it.totalAmount }

        var profit = 0.0
        transactions.forEach { transaction ->
            transaction.itemsSummary.split(", ").forEach { item ->
                val name = item.substringBeforeLast(" x", item)
                val quantity = item.substringAfterLast(" x", "").toIntOrNull()
                if (quantity != null) {
                    val product = products.firstOrNull { it.name == name }
                    if (product != null) {
                        profit += (product.price - product.costPrice) * quantity
                    }
                }
            }
        }

        val lowStock = products.count { it.stock < 5 }

        val expiryFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val limit = (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 30)
        }

        val expiring = products.count { product ->
            val expiry = product.expiryDate ?: return@count false
            val date = runCatching { expiryFormat.parse(expiry) }.getOrNull()
                ?: return@count false
            date.time in today.timeInMillis..limit.timeInMillis
        }

        return DashboardSummary(
            revenue = revenue,
            profit = profit,
            lowStock = lowStock,
            expiring = expiring
        )
    }

    private fun formatCurrency(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return "Rp ${formatter.format(value)}"
    }

    private data class DashboardSummary(
        val revenue: Double,
        val profit: Double,
        val lowStock: Int,
        val expiring: Int
    )
}
