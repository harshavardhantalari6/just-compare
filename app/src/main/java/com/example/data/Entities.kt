package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String, // String ID (e.g. unique hash or slug representation)
    val name: String,
    val category: String,
    val imageUrl: String,
    val description: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val minPrice: Double,
    val averageRating: Double
)

@Entity(tableName = "product_deals")
data class ProductDealEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: String,
    val platform: String, // "Amazon", "Flipkart", "eBay", etc.
    val price: Double,
    val originalPrice: Double?, // For showing price drops
    val rating: Double,
    val reviewCount: Int,
    val url: String,
    val seller: String,
    val availability: String, // "In Stock", "Out of Stock"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "price_history")
data class PriceHistoryPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: String,
    val platform: String,
    val price: Double,
    val timestamp: Long
)

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: String,
    val productName: String,
    val imageUrl: String,
    val targetPrice: Double,
    val currentMinPrice: Double,
    val creationTimestamp: Long = System.currentTimeMillis(),
    val isTriggered: Boolean = false
)

@Entity(tableName = "search_history")
data class SearchQueryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
