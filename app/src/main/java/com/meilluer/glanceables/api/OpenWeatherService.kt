package com.meilluer.glanceables.api

import com.meilluer.glanceables.data.AirPollutionResponse
import com.meilluer.glanceables.data.UvIndexResponse
import com.meilluer.glanceables.data.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for OpenWeather API (2.5 free-tier endpoints).
 *
 * All endpoints require an `appid` query parameter (the API key).
 */
interface OpenWeatherService {

    /**
     * Current weather data.
     * https://openweathermap.org/current
     */
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse

    /**
     * UV Index at a location.
     * https://openweathermap.org/api/uvi
     */
    @GET("data/2.5/uvi")
    suspend fun getUvIndex(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): UvIndexResponse

    /**
     * Air pollution / AQI data.
     * https://openweathermap.org/api/air-pollution
     */
    @GET("data/2.5/air_pollution")
    suspend fun getAirPollution(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): AirPollutionResponse
}
