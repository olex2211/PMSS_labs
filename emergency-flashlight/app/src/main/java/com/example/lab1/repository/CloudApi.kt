package com.example.lab1.repository

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/** Firebase Realtime Database answers a POST with the generated key: {"name": "-Oabc..."}. */
data class UploadResponse(val name: String)

interface CloudApi {
    /** Appends one record under /readings. A failed status code or no network throws. */
    @POST("readings.json")
    suspend fun upload(@Body payload: SensorPayload): UploadResponse
}

object CloudApiFactory {
    fun create(baseUrl: String = CloudConfig.BASE_URL): CloudApi {
        val logging = HttpLoggingInterceptor { message -> Log.i(CloudConfig.TAG, message) }
            .setLevel(HttpLoggingInterceptor.Level.BASIC)
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudApi::class.java)
    }
}
