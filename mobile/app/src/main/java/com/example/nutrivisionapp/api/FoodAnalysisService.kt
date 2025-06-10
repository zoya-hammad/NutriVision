package com.example.nutrivisionapp.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.nutrivisionapp.BuildConfig
import retrofit2.http.Body
import retrofit2.http.POST

data class FoodAnalysis(
    val calories: Int? = null,
    val glycemicLoad: Int? = null,
    val advice: String? = null
) {
    // Explicit no-arg constructor for Firebase
    constructor() : this(null, null, null)
}

class FoodAnalysisService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
    
    // Modal deployment URL
    private val baseUrl = BuildConfig.FOOD_ANALYSIS_BASE_URL

    suspend fun analyzeFood(
        foodName: String,
        description: String,
        userAge: String?,
        dietaryRestrictions: String?,
        allergies: String?
    ): Result<FoodAnalysis> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("food_name", foodName)
                put("description", description)
                put("user_age", userAge)
                put("dietary_restrictions", dietaryRestrictions)
                put("allergies", allergies)
            }.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/analyze-food")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(Exception("API call failed: ${response.code}"))
            }

            parseAnalysisResponse(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAnalysisResponse(response: String): Result<FoodAnalysis> {
        return try {
            val json = JSONObject(response)
            val analysis = FoodAnalysis(
                calories = json.getInt("calories"),
                glycemicLoad = when (json.getString("glycemic_load").lowercase()) {
                    "low" -> 1
                    "medium" -> 2
                    "high" -> 3
                    else -> 2
                },
                advice = json.getString("advice")
            )
            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

interface FoodAnalysisApi {
    @POST("/analyze-food")
    suspend fun analyzeFood(@Body request: FoodAnalysisRequest): FoodAnalysisResponse

    companion object {
        private const val BASE_URL = BuildConfig.FOOD_ANALYSIS_BASE_URL

        fun create(): FoodAnalysisApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FoodAnalysisApi::class.java)
        }
    }
}

data class FoodAnalysisRequest(
    val food_name: String,
    val description: String,
    val user_age: String? = null,
    val dietary_restrictions: String? = null,
    val allergies: String? = null
)

data class FoodAnalysisResponse(
    val calories: Int,
    val glycemic_load: String,
    val advice: String
) 