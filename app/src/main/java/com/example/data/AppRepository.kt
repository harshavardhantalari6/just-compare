package com.example.data

import com.example.network.GeminiSearchService
import com.example.network.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class AppRepository(private val appDao: AppDao) {

    val allSearchHistory: Flow<List<SearchQueryEntity>> = appDao.getSearchHistoryFlow()
    val allSavedAlerts: Flow<List<PriceAlertEntity>> = appDao.getAllAlertsFlow()

    fun getDealsForProductFlow(productId: String): Flow<List<ProductDealEntity>> {
        return appDao.getDealsForProductFlow(productId)
    }

    fun getPriceHistoryFlow(productId: String): Flow<List<PriceHistoryPointEntity>> {
        return appDao.getPriceHistoryFlow(productId)
    }

    suspend fun getProductById(productId: String): ProductEntity? {
        return appDao.getProductById(productId)
    }

    // Perform web aggregation search action
    suspend fun searchAndPopulate(query: String): SearchResult {
        val trimmedQuery = query.trim()
        
        // 1. Add query to local search history
        if (trimmedQuery.isNotEmpty()) {
            appDao.insertSearchQuery(SearchQueryEntity(query = trimmedQuery))
        }

        // 2. Perform live network scraper aggregation (via Gemini or high-quality simulation)
        val result = GeminiSearchService.performSearch(trimmedQuery)

        // 3. Save products & deals to our local SQLite database as a smart cache
        for (product in result.products) {
            appDao.insertProduct(product)
            
            // Delete old deals to keep fresh real-time pricing
            appDao.deleteDealsForProduct(product.id)
            val productDeals = result.deals.filter { it.productId == product.id }
            appDao.insertDeals(productDeals)

            // Save historical price trend points for graphing
            val productHistory = result.history.filter { it.productId == product.id }
            appDao.insertHistoryPoints(productHistory)
        }

        // 4. Process matches and evaluate saved price alerts
        val currentAlerts = allSavedAlerts.run { firstOrNull() } ?: emptyList()
        if (currentAlerts.isNotEmpty()) {
            for (alert in currentAlerts) {
                // Find if we returned new prices for this product
                val matchedProduct = result.products.find { it.id == alert.productId }
                if (matchedProduct != null) {
                    val currentMin = matchedProduct.minPrice
                    val triggered = (currentMin <= alert.targetPrice)
                    appDao.updateAlertTriggerStatus(alert.id, triggered, currentMin)
                }
            }
        }

        return result
    }

    suspend fun saveAlert(productId: String, productName: String, imageUrl: String, targetPrice: Double, currentPrice: Double) {
        val alert = PriceAlertEntity(
            productId = productId,
            productName = productName,
            imageUrl = imageUrl,
            targetPrice = targetPrice,
            currentMinPrice = currentPrice,
            isTriggered = (currentPrice <= targetPrice)
        )
        appDao.insertAlert(alert)
    }

    suspend fun deleteAlert(alert: PriceAlertEntity) {
        appDao.deleteAlert(alert)
    }

    suspend fun clearHistory() {
        appDao.clearSearchHistory()
    }
    
    suspend fun deleteSearchQuery(id: Int) {
        appDao.deleteSearchQueryById(id)
    }
}
