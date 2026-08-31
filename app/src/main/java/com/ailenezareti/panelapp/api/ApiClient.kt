package com.ailenezareti.panelapp.api

import android.content.Context
import com.ailenezareti.panelapp.BuildConfig
import com.ailenezareti.panelapp.Prefs
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var service: ApiService? = null

    fun get(context: Context): ApiService {
        service?.let { return it }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = Prefs.token(context.applicationContext)
                val request = chain.request().newBuilder().apply {
                    if (token.isNotBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                chain.proceed(request)
            }
            .build()

        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/') + "/"

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java).also { service = it }
    }
}
