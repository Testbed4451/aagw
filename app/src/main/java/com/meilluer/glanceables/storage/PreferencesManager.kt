package com.meilluer.glanceables.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.meilluer.glanceables.data.WeatherData

/**
 * Manages persistent storage of the OpenWeather API key and cached weather data
 * using SharedPreferences.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    // ── API Key ───────────────────────────────────────────────────────────────

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    // ── Latitude / Longitude (last used) ──────────────────────────────────────

    fun getLatitude(): Double =
        prefs.getString(KEY_LATITUDE, "0.0")?.toDoubleOrNull() ?: 0.0

    fun getLongitude(): Double =
        prefs.getString(KEY_LONGITUDE, "0.0")?.toDoubleOrNull() ?: 0.0

    fun setLocation(lat: Double, lon: Double) {
        prefs.edit()
            .putString(KEY_LATITUDE, lat.toString())
            .putString(KEY_LONGITUDE, lon.toString())
            .apply()
    }

    fun hasLocation(): Boolean = getLatitude() != 0.0 || getLongitude() != 0.0

    // ── Cached Weather Data ───────────────────────────────────────────────────

    fun getCachedWeatherData(): WeatherData? {
        val json = prefs.getString(KEY_WEATHER_DATA, null) ?: return null
        return try {
            gson.fromJson(json, WeatherData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun setCachedWeatherData(data: WeatherData) {
        prefs.edit().putString(KEY_WEATHER_DATA, gson.toJson(data)).apply()
    }

    companion object {
        private const val PREFS_NAME = "glanceables_prefs"
        private const val KEY_API_KEY = "openweather_api_key"
        private const val KEY_LATITUDE = "last_latitude"
        private const val KEY_LONGITUDE = "last_longitude"
        private const val KEY_WEATHER_DATA = "cached_weather_data"
    }
}
