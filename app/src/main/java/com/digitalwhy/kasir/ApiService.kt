package com.digitalwhy.kasir

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class ProductResponse(val status: String, val data: Product?)
data class ProductsResponse(val status: String, val data: List<Product>?)

data class TransactionItemRequest(val product_id: String, val quantity: Double)
data class TransactionRequest(val items: List<TransactionItemRequest>)
data class TransactionResponse(val status: String, val message: String?)

interface ApiService {
    @GET("api/products")
    suspend fun getAllProducts(): Response<ProductsResponse>

    @GET("api/products/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): Response<ProductResponse>

    @POST("api/products")
    suspend fun addProduct(@Body product: Product): Response<ProductResponse>

    @POST("api/transactions")
    suspend fun createTransaction(@Body request: TransactionRequest): Response<TransactionResponse>
}

object ApiClient {
    private const val BASE_URL = "http://127.0.0.1:8002/"
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
