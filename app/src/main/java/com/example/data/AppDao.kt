package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Products
    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductById(productId: String): ProductEntity?

    @Query("SELECT * FROM products ORDER BY lastUpdated DESC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    // Product Deals
    @Query("SELECT * FROM product_deals WHERE productId = :productId ORDER BY price ASC")
    fun getDealsForProductFlow(productId: String): Flow<List<ProductDealEntity>>

    @Query("SELECT * FROM product_deals WHERE productId = :productId ORDER BY price ASC")
    suspend fun getDealsForProduct(productId: String): List<ProductDealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeals(deals: List<ProductDealEntity>)

    @Query("DELETE FROM product_deals WHERE productId = :productId")
    suspend fun deleteDealsForProduct(productId: String)

    // Price History
    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY timestamp ASC")
    fun getPriceHistoryFlow(productId: String): Flow<List<PriceHistoryPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryPoints(points: List<PriceHistoryPointEntity>)

    // Price Alerts
    @Query("SELECT * FROM price_alerts ORDER BY creationTimestamp DESC")
    fun getAllAlertsFlow(): Flow<List<PriceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PriceAlertEntity)

    @Delete
    suspend fun deleteAlert(alert: PriceAlertEntity)

    @Query("UPDATE price_alerts SET isTriggered = :triggered, currentMinPrice = :currentMinPrice WHERE id = :alertId")
    suspend fun updateAlertTriggerStatus(alertId: Int, triggered: Boolean, currentMinPrice: Double)

    // Search History
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistoryFlow(): Flow<List<SearchQueryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchQueryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchQueryById(id: Int)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
