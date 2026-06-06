package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.GeminiSearchService
import com.example.network.SearchResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface SearchState {
    object Idle : SearchState
    object Loading : SearchState
    data class Success(val products: List<ProductEntity>, val isSimulation: Boolean) : SearchState
    data class Error(val message: String) : SearchState
}

enum class ActiveTab {
    SEARCH, ALERTS, HISTORY
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AppRepository(database.appDao())

    // UI inputs
    var searchText = MutableStateFlow("")
        private set

    var selectedProduct = MutableStateFlow<ProductEntity?>(null)
        private set

    var activeTab = MutableStateFlow(ActiveTab.SEARCH)
        private set

    // State flows
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    val searchHistory: StateFlow<List<SearchQueryEntity>> = repository.allSearchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedAlerts: StateFlow<List<PriceAlertEntity>> = repository.allSavedAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observable flows for the selected product's details
    val selectedProductDeals: StateFlow<List<ProductDealEntity>> = selectedProduct
        .flatMapLatest { product ->
            if (product != null) {
                repository.getDealsForProductFlow(product.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProductHistory: StateFlow<List<PriceHistoryPointEntity>> = selectedProduct
        .flatMapLatest { product ->
            if (product != null) {
                repository.getPriceHistoryFlow(product.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Check if the Gemini API Key is configured to show a status header
    val isApiKeyAvailable: Boolean
        get() = GeminiSearchService.isApiKeyAvailable()

    fun updateSearchText(text: String) {
        searchText.value = text
    }

    fun selectTab(tab: ActiveTab) {
        activeTab.value = tab
        // If clicking tab, clear selected product to reset detail view to list view
        if (tab != ActiveTab.SEARCH) {
            selectedProduct.value = null
        }
    }

    fun selectProduct(product: ProductEntity?) {
        selectedProduct.value = product
    }

    fun performSearch(query: String) {
        if (query.trim().isEmpty()) return
        
        searchText.value = query
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                val result = repository.searchAndPopulate(query)
                if (result.products.isEmpty()) {
                    _searchState.value = SearchState.Error("No matching products found. Try another query!")
                } else {
                    _searchState.value = SearchState.Success(result.products, result.isSimulation)
                }
            } catch (e: Exception) {
                _searchState.value = SearchState.Error("Failed to fetch price comparison: ${e.localizedMessage}")
            }
        }
    }

    fun createPriceAlert(product: ProductEntity, targetPrice: Double) {
        viewModelScope.launch {
            repository.saveAlert(
                productId = product.id,
                productName = product.name,
                imageUrl = product.imageUrl,
                targetPrice = targetPrice,
                currentPrice = product.minPrice
            )
        }
    }

    fun deletePriceAlert(alert: PriceAlertEntity) {
        viewModelScope.launch {
            repository.deleteAlert(alert)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteSearchQuery(id: Int) {
        viewModelScope.launch {
            repository.deleteSearchQuery(id)
        }
    }
}
