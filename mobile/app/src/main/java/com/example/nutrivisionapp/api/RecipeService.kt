package com.example.nutrivisionapp.api

import com.example.nutrivisionapp.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// API Interface
interface RecipeService {
    @POST("recommend_recipe")
    fun getRecipe(@Body request: RecipeRequest): Call<Recipe>
}

// Request Data Class
data class RecipeRequest(
    val query: String,
    val num_recipes: Int = 3  // Default to 3 recipes
)

// Response Data Class
data class Recipe(
    val title: String,
    val glycemicLoad: Double,
    val ingredients: List<String>,
    val instructions: List<String>
)

// API Client
object RecipeClient {
    private val BASE_URL = BuildConfig.RECIPE_BASE_URL

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val recipeApi: RecipeService = retrofit.create(RecipeService::class.java)
} 