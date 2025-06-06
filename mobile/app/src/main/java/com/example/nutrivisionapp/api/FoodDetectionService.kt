package com.example.nutrivisionapp.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FoodDetectionService {
    @Multipart
    @POST("/")
    suspend fun detectFood(@Part image: MultipartBody.Part): Response<FoodDetectionResponse>
}

data class FoodDetectionResponse(
    val prediction: String
) 