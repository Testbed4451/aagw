package com.meilluer.glanceables.repository

import com.meilluer.glanceables.api.RetrofitClient
import com.meilluer.glanceables.data.WeatherData

/**
 * Repository that fetches weather, UV, and AQI data from OpenWeather API
 * and combines them into a single [WeatherData] object.
 */
class WeatherRepository {

    private val service = RetrofitClient.service

    /**
     * Fetch all weather data for the given coordinates.
     *
     * Makes three parallel-safe calls: current weather, UV index, air pollution.
     * Returns a combined [WeatherData] on success, throws on failure.
     */
    suspend fun fetchWeatherData(lat: Double, lon: Double, apiKey: String): WeatherData {
        // Fetch current weather
        val weather = service.getCurrentWeather(lat, lon, apiKey)

        // Fetch UV index
        val uv = try {
            service.getUvIndex(lat, lon, apiKey)
        } catch (e: Exception) {
            // UV endpoint may not be available on all free keys; fall back to 0
            null
        }

        // Fetch air pollution
        val airPollution = try {
            service.getAirPollution(lat, lon, apiKey)
        } catch (e: Exception) {
            null
        }

        val aqiEntry = airPollution?.list?.firstOrNull()

        val aqi = aqiEntry?.main?.aqi ?: 0
        return WeatherData(
            temperature = weather.main.temp,
            feelsLike = weather.main.feelsLike,
            humidity = weather.main.humidity,
            uvIndex = uv?.value ?: 0.0,
            aqi = aqi,
            aqiLabel = WeatherData.aqiToLabel(aqi),
            pm25 = aqiEntry?.components?.pm25 ?: 0.0,
            cityName = weather.cityName,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
