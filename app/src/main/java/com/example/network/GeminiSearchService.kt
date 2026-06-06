package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiSearchService {
    private const val TAG = "GeminiSearchService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Flag indicating whether we have a real API key or are using fallback simulation
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return !key.isNullOrEmpty() && key != "MY_GEMINI_API_KEY" && key != "placeholder"
    }

    suspend fun performSearch(query: String): SearchResult = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext performSimulatedSearch(query)
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "$BASE_URL?key=$apiKey"

        val prompt = """
            You are a real-time e-commerce aggregator and price comparison engine. 
            User is searching for: "$query".
            
            Perform a simulated live price comparison search across top websites (Amazon.in, Flipkart, Croma, Reliance Digital, or eBay depends on local context) for the best matching products.
            Generate 2-3 genuine matching products, and for each product, generate 3-4 store deals showing different price scenarios.
            Also, generate a list of 5 historical price trend data points for this product over the last 6 months to display in a chart.
            
            Return the result in raw JSON following this exact structure:
            {
              "products": [
                {
                  "name": "Detailed product title, e.g., Apple iPhone 15 Pro (128 GB, Natural Titanium)",
                  "category": "Electronics / Smartphones",
                  "imageUrl": "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=300", 
                  "description": "Short description of key specs (e.g., A17 Pro chip, Action button, USB-C).",
                  "averageRating": 4.6,
                  "deals": [
                    {
                      "platform": "Amazon",
                      "price": 129990.0,
                      "originalPrice": 134900.0,
                      "rating": 4.6,
                      "reviewCount": 1420,
                      "url": "https://www.amazon.in/s?k=iphone+15+pro",
                      "seller": "Appario Retail Private Ltd",
                      "availability": "In Stock"
                    },
                    {
                      "platform": "Flipkart",
                      "price": 128990.0,
                      "originalPrice": 134900.0,
                      "rating": 4.5,
                      "reviewCount": 850,
                      "url": "https://www.flipkart.com/search?q=iphone+15+pro",
                      "seller": "SuperComNet",
                      "availability": "In Stock"
                    },
                    {
                      "platform": "Croma",
                      "price": 129500.0,
                      "originalPrice": 134900.0,
                      "rating": 4.4,
                      "reviewCount": 95,
                      "url": "https://www.croma.com/search?text=iphone+15+pro",
                      "seller": "Croma Digital",
                      "availability": "In Stock"
                    }
                  ],
                  "priceHistory": [
                    { "platform": "Amazon", "price": 134900.0, "timestamp": 1704067200000 },
                    { "platform": "Amazon", "price": 132000.0, "timestamp": 1706745600000 },
                    { "platform": "Amazon", "price": 131000.0, "timestamp": 1709251200000 },
                    { "platform": "Amazon", "price": 129990.0, "timestamp": 1711929600000 },
                    { "platform": "Amazon", "price": 129990.0, "timestamp": 1714521600000 }
                  ]
                }
              ]
            }
            
            Use realistic price values (INR or equivalentUSD based on the query's regional context). Ensure the image URL is valid or use clean descriptive unsplash URLs (smartphones: photo-1511707171634-5f897ff02aa9, laptops: photo-1496181130204-755241524eab, headphones: photo-1505740420928-5e560c06d30e, watches: photo-1523275335684-37898b6baf30).
            Only output raw JSON. Do NOT include markdown code fences like ```json ... ```. Just return the JSON object directly.
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
            })
        }

        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Unsuccessful response from Gemini API: ${response.code} ${response.message}")
                return@withContext performSimulatedSearch(query)
            }

            val bodyString = response.body?.string() ?: throw Exception("Empty response body")
            val jsonResponse = JSONObject(bodyString)
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() == 0) {
                return@withContext performSimulatedSearch(query)
            }
            val firstPart = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
            
            val rawJsonText = firstPart.getString("text").trim()
            
            // Clean up any potential markdown decoration just in case
            val cleanJson = if (rawJsonText.startsWith("```")) {
                val clean = rawJsonText
                    .removePrefix("```json")
                    .removePrefix("```")
                if (clean.endsWith("```")) {
                    clean.removeSuffix("```").trim()
                } else {
                    clean.trim()
                }
            } else {
                rawJsonText
            }

            return@withContext parseGeminiJson(cleanJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing search via Gemini, returning backup simulation: ${e.message}", e)
            return@withContext performSimulatedSearch(query)
        }
    }

    private fun parseGeminiJson(jsonString: String): SearchResult {
        try {
            val root = JSONObject(jsonString)
            val productsArray = root.getJSONArray("products")
            val products = mutableListOf<ProductEntity>()
            val deals = mutableListOf<ProductDealEntity>()
            val history = mutableListOf<PriceHistoryPointEntity>()

            for (i in 0 until productsArray.length()) {
                val pObj = productsArray.getJSONObject(i)
                val productName = pObj.getString("name")
                val productId = UUID.nameUUIDFromBytes(productName.toByteArray()).toString()
                
                val pImage = pObj.optString("imageUrl", "")
                val finalImage = if (pImage.isEmpty() || !pImage.startsWith("http")) {
                    getFallbackUnsplashUrl(productName)
                } else pImage

                val product = ProductEntity(
                    id = productId,
                    name = productName,
                    category = pObj.optString("category", "General"),
                    imageUrl = finalImage,
                    description = pObj.optString("description", ""),
                    minPrice = pObj.optDouble("minPrice", 0.0),
                    averageRating = pObj.optDouble("averageRating", 4.0)
                )

                val dealsArray = pObj.getJSONArray("deals")
                val pDeals = mutableListOf<ProductDealEntity>()
                for (j in 0 until dealsArray.length()) {
                    val dObj = dealsArray.getJSONObject(j)
                    val deal = ProductDealEntity(
                        productId = productId,
                        platform = dObj.getString("platform"),
                        price = dObj.getDouble("price"),
                        originalPrice = if (dObj.has("originalPrice") && !dObj.isNull("originalPrice")) dObj.getDouble("originalPrice") else null,
                        rating = dObj.optDouble("rating", 4.0),
                        reviewCount = dObj.optInt("reviewCount", 50),
                        url = dObj.optString("url", "https://google.com"),
                        seller = dObj.optString("seller", "Direct Store"),
                        availability = dObj.optString("availability", "In Stock")
                    )
                    deals.add(deal)
                    pDeals.add(deal)
                }

                // If min price is not specified, let's compute it
                val minPriceComputed = if (pDeals.isNotEmpty()) pDeals.minOf { it.price } else 0.0
                products.add(product.copy(minPrice = minPriceComputed))

                // History
                if (pObj.has("priceHistory")) {
                    val histArray = pObj.getJSONArray("priceHistory")
                    for (k in 0 until histArray.length()) {
                        val hObj = histArray.getJSONObject(k)
                        history.add(
                            PriceHistoryPointEntity(
                                productId = productId,
                                platform = hObj.optString("platform", "General"),
                                price = hObj.getDouble("price"),
                                timestamp = hObj.getLong("timestamp")
                            )
                        )
                    }
                } else {
                    // Generate fallback history
                    history.addAll(generateFallbackHistory(productId, minPriceComputed))
                }
            }

            return SearchResult(products, deals, history, isSimulation = false)
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure parsing Gemini JSON, returning fallback search models.", e)
            return performSimulatedSearch("Fallback parsing error - " + e.localizedMessage)
        }
    }

    // High quality mock search for any queries (running offline or with default API key)
    private fun performSimulatedSearch(query: String): SearchResult {
        val queryLower = query.lowercase().trim()
        val products = mutableListOf<ProductEntity>()
        val deals = mutableListOf<ProductDealEntity>()
        val history = mutableListOf<PriceHistoryPointEntity>()

        // Predefined models for realistic offline comparisons
        val itemsToGenerate = when {
            queryLower.contains("phone") || queryLower.contains("iphone") || queryLower.contains("samsung") || queryLower.contains("pixel") -> {
                listOf(
                    MockItem("Apple iPhone 15 Pro (128 GB, Black)", "Smartphones", 119900.0, "photo-1511707171634-5f897ff02aa9", "A17 Pro Titanium smartphone, superior camera, action button."),
                    MockItem("Samsung Galaxy S24 Ultra (256 GB, Gray)", "Smartphones", 124999.0, "photo-1610945265064-0e34e5519bbf", "Snapdragon Gen 3, integrated S-Pen, incredible 200MP camera zoom.")
                )
            }
            queryLower.contains("laptop") || queryLower.contains("macbook") || queryLower.contains("dell") || queryLower.contains("hp") -> {
                listOf(
                    MockItem("Apple MacBook Air M3 (13-inch, 16GB)", "Laptops", 114900.0, "photo-1517336714731-489689fd1ca8", "The thin, light MacBook Air with high-performance Apple M3 silicon."),
                    MockItem("Dell XPS 13 Core Ultra (16GB, 512GB)", "Laptops", 129000.0, "photo-1496181130204-755241524eab", "Border Intel Core Ultra portable with beautiful infinite edge OLED screen.")
                )
            }
            queryLower.contains("headphones") || queryLower.contains("sony") || queryLower.contains("bose") || queryLower.contains("earbuds") -> {
                listOf(
                    MockItem("Sony WH-1000XM5 Noise Cancelling", "Audio / Headwear", 29900.0, "photo-1505740420928-5e560c06d30e", "Industry leading active noise cancellation, smart adaptive sound controls."),
                    MockItem("Bose QuietComfort Ultra Headphones", "Audio / Headwear", 35900.0, "photo-1546435770-a3e426bf472b", "Immersive audio experience, custom active cancellation, supreme comfort.")
                )
            }
            queryLower.contains("watch") || queryLower.contains("fitbit") || queryLower.contains("apple watch") || queryLower.contains("garmin") -> {
                listOf(
                    MockItem("Apple Watch Series 9 (GPS, 45mm)", "Wearables", 41900.0, "photo-1508685096489-7aacd43bd3b1", "Always-on retina display, blood oxygen tracking, crash detection system."),
                    MockItem("Garmin Venu 3 GPS Smartwatch", "Wearables", 44990.0, "photo-1523275335684-37898b6baf30", "Crisp AMOLED display, extensive health tracking, up to 14 days of battery.")
                )
            }
            else -> {
                val capitalQuery = query.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                val displayQuery = if (capitalQuery.isEmpty()) "Standard Product" else capitalQuery
                listOf(
                    MockItem("$displayQuery Premium Edition", "Consumer Electronics", 49999.0, "photo-1523275335684-37898b6baf30", "Our top compared selection for $displayQuery. Includes standard 1-year product support."),
                    MockItem("$displayQuery Starter Bundle", "Consumer Electronics", 38500.0, "photo-1526170375885-4d8ecf77b99f", "Great value configuration package of $displayQuery with companion bundle kit.")
                )
            }
        }

        for (mock in itemsToGenerate) {
            val productId = UUID.nameUUIDFromBytes(mock.name.toByteArray()).toString()
            val imageUrl = "https://images.unsplash.com/${mock.unsplashId}?auto=format&fit=crop&w=300&q=80"
            
            val amazonPrice = mock.basePrice
            val flipkartPrice = mock.basePrice * 0.985 // Slightly lower
            val cromaPrice = mock.basePrice * 1.015 // Slightly higher
            
            val pDeals = listOf(
                ProductDealEntity(
                    productId = productId,
                    platform = "Amazon",
                    price = amazonPrice,
                    originalPrice = mock.basePrice * 1.10,
                    rating = 4.7,
                    reviewCount = 1205,
                    url = "https://www.amazon.in/s?k=${mock.name}",
                    seller = "Appario Retail",
                    availability = "In Stock"
                ),
                ProductDealEntity(
                    productId = productId,
                    platform = "Flipkart",
                    price = flipkartPrice,
                    originalPrice = mock.basePrice * 1.10,
                    rating = 4.5,
                    reviewCount = 840,
                    url = "https://www.flipkart.com/search?q=${mock.name}",
                    seller = "SuperComNet",
                    availability = "In Stock"
                ),
                ProductDealEntity(
                    productId = productId,
                    platform = "Croma",
                    price = cromaPrice,
                    originalPrice = mock.basePrice * 1.05,
                    rating = 4.4,
                    reviewCount = 143,
                    url = "https://www.croma.com/search?text=${mock.name}",
                    seller = "Croma Digital",
                    availability = "In Stock"
                )
            )

            val minPrice = pDeals.minOf { it.price }
            val product = ProductEntity(
                id = productId,
                name = mock.name,
                category = mock.category,
                imageUrl = imageUrl,
                description = mock.description,
                minPrice = minPrice,
                averageRating = 4.5
            )

            products.add(product)
            deals.addAll(pDeals)
            history.addAll(generateFallbackHistory(productId, minPrice))
        }

        return SearchResult(products, deals, history, isSimulation = true)
    }

    private fun generateFallbackHistory(productId: String, basePrice: Double): List<PriceHistoryPointEntity> {
        val list = mutableListOf<PriceHistoryPointEntity>()
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val platforms = listOf("Amazon", "Flipkart")
        
        for (p in platforms) {
            val variance = if (p == "Amazon") 1.0 else 0.98
            for (i in 5 downTo 0) {
                // Fluctuating historical prices
                val monthOffset = i * 30 * oneDayMs
                val time = now - monthOffset
                val d = i.toDouble() + (p.hashCode() % 5).toDouble()
                val factor = 1.0 + (Math.sin(d) * 0.05) // Up/down 5%
                list.add(
                    PriceHistoryPointEntity(
                        productId = productId,
                        platform = p,
                        price = (basePrice * factor * variance).toInt().toDouble(),
                        timestamp = time
                    )
                )
            }
        }
        return list
    }

    private fun getFallbackUnsplashUrl(name: String): String {
        val nameLower = name.lowercase()
        val suffix = when {
            nameLower.contains("phone") || nameLower.contains("iphone") || nameLower.contains("pixel") -> "photo-1511707171634-5f897ff02aa9"
            nameLower.contains("laptop") || nameLower.contains("macbook") || nameLower.contains("dell") -> "photo-1496181130204-755241524eab"
            nameLower.contains("headphone") || nameLower.contains("audio") || nameLower.contains("sound") -> "photo-1505740420928-5e560c06d30e"
            nameLower.contains("watch") || nameLower.contains("smartwatch") -> "photo-1523275335684-37898b6baf30"
            nameLower.contains("camera") || nameLower.contains("lens") -> "photo-1516035069371-29a1b244cc32"
            nameLower.contains("game") || nameLower.contains("console") || nameLower.contains("ps5") || nameLower.contains("nintendo") -> "photo-1605901309584-818e25960a8f"
            else -> "photo-1526170375885-4d8ecf77b99f"
        }
        return "https://images.unsplash.com/$suffix?auto=format&fit=crop&w=300&q=80"
    }

    private data class MockItem(
        val name: String,
        val category: String,
        val basePrice: Double,
        val unsplashId: String,
        val description: String
    )
}

data class SearchResult(
    val products: List<ProductEntity>,
    val deals: List<ProductDealEntity>,
    val history: List<PriceHistoryPointEntity>,
    val isSimulation: Boolean
)
