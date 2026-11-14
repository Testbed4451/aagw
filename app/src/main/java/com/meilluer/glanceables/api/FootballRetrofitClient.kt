package com.meilluer.glanceables.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client for football-data.org API v4.
 *
 * Includes a rate-limiting interceptor that enforces the free-tier cap
 * of 10 requests per minute using a simple token-bucket approach.
 */
object FootballRetrofitClient {

    private const val BASE_URL = "https://api.football-data.org/v4/"

    /** Maximum requests allowed within the rate window. */
    private const val MAX_REQUESTS_PER_MINUTE = 10

    /** Rate window duration in milliseconds (1 minute). */
    private const val RATE_WINDOW_MS = 60_000L

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * Simple token-bucket rate limiter.
     *
     * Keeps track of request timestamps within the current window and blocks
     * (via [Thread.sleep]) if the limit would be exceeded.
     */
    private val rateLimitInterceptor = object : Interceptor {
        private val requestTimestamps = mutableListOf<Long>()

        @Synchronized
        override fun intercept(chain: Interceptor.Chain): Response {
            val now = System.currentTimeMillis()

            // Evict timestamps older than the rate window
            requestTimestamps.removeAll { it < now - RATE_WINDOW_MS }

            if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
                // Wait until the oldest request falls outside the window
                val waitMs = requestTimestamps.first() + RATE_WINDOW_MS - now
                if (waitMs > 0) {
                    Thread.sleep(waitMs)
                }
                // Evict again after sleeping
                val afterSleep = System.currentTimeMillis()
                requestTimestamps.removeAll { it < afterSleep - RATE_WINDOW_MS }
            }

            requestTimestamps.add(System.currentTimeMillis())
            return chain.proceed(chain.request())
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(rateLimitInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: FootballApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FootballApiService::class.java)
    }
}
