package com.digitalwhy.kasir

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// Model untuk menangkap balasan JSON dari Laravel
data class ProductResponse(
    val status: String,
    val data: Product? // Menggunakan model Product yang sudah kita buat
)

interface ApiService {
    @GET("api/products/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): Response<ProductResponse>
}

object ApiClient {
    // PASTIKAN PORT 8002 SESUAI DENGAN LARAVEL
    private const val BASE_URL = "http://127.0.0.1:8002/"
    
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
