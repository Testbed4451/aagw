package com.meilluer.glanceables.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.meilluer.glanceables.data.TravelInfo
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

    // ── Refresh Interval ──────────────────────────────────────────────────────

    fun getRefreshIntervalMinutes(): Long {
        return prefs.getLong(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL_MINUTES)
    }

    fun setRefreshIntervalMinutes(minutes: Long) {
        prefs.edit().putLong(KEY_REFRESH_INTERVAL_MINUTES, minutes).apply()
    }

    // ── Travel Information (Extracted from PDF / Local Storage) ──────────────

    fun saveTravelInfo(info: TravelInfo) {
        prefs.edit()
            .putString(KEY_SEAT_NUMBER, info.seatNumber.trim())
            .putString(KEY_BIRTHTYPE, info.birthtype.trim())
            .putString(KEY_DATE_OF_JOURNEY, info.dateOfJourney.trim())
            .putString(KEY_DEPARTURE_TIME, info.departureTime.trim())
            .putString(KEY_ARRIVAL_TIME, info.arrivalTime.trim())
            .putString(KEY_BOARDING_STATION, info.boardingStation.trim())
            .putString(KEY_DEBOARDING_STATION, info.deboardingStation.trim())
            .putString(KEY_TRAVEL_INFO, gson.toJson(info))
            .apply()
    }

    fun getTravelInfo(): TravelInfo {
        val json = prefs.getString(KEY_TRAVEL_INFO, null)
        if (!json.isNullOrBlank()) {
            try {
                val data = gson.fromJson(json, TravelInfo::class.java)
                if (data != null) return data
            } catch (e: Exception) {
                // fallback to individual fields
            }
        }
        return TravelInfo(
            seatNumber = prefs.getString(KEY_SEAT_NUMBER, "") ?: "",
            birthtype = prefs.getString(KEY_BIRTHTYPE, "") ?: "",
            dateOfJourney = prefs.getString(KEY_DATE_OF_JOURNEY, "") ?: "",
            departureTime = prefs.getString(KEY_DEPARTURE_TIME, "") ?: "",
            arrivalTime = prefs.getString(KEY_ARRIVAL_TIME, "") ?: "",
            boardingStation = prefs.getString(KEY_BOARDING_STATION, "") ?: "",
            deboardingStation = prefs.getString(KEY_DEBOARDING_STATION, "") ?: ""
        )
    }

    fun hasTravelInfo(): Boolean = getTravelInfo().isNotEmpty()

    fun clearTravelInfo() {
        prefs.edit()
            .remove(KEY_SEAT_NUMBER)
            .remove(KEY_BIRTHTYPE)
            .remove(KEY_DATE_OF_JOURNEY)
            .remove(KEY_DEPARTURE_TIME)
            .remove(KEY_ARRIVAL_TIME)
            .remove(KEY_BOARDING_STATION)
            .remove(KEY_DEBOARDING_STATION)
            .remove(KEY_TRAVEL_INFO)
            .apply()
    }

    // Individual field accessors for the travel variables
    fun getSeatNumber(): String = prefs.getString(KEY_SEAT_NUMBER, "") ?: ""
    fun setSeatNumber(value: String) {
        prefs.edit().putString(KEY_SEAT_NUMBER, value.trim()).apply()
        saveTravelInfo(getTravelInfo().copy(seatNumber = value.trim()))
    }

    fun getBirthtype(): String = prefs.getString(KEY_BIRTHTYPE, "") ?: ""
    fun setBirthtype(value: String) {
        prefs.edit().putString(KEY_BIRTHTYPE, value.trim()).apply()
        saveTravelInfo(getTravelInfo().copy(birthtype = value.trim()))
    }

    fun getDateOfJourney(): String = prefs.getString(KEY_DATE_OF_JOURNEY, "") ?: ""
    fun setDateOfJourney(value: String) {
        prefs.edit().putString(KEY_DATE_OF_JOURNEY, value.trim()).apply()
        saveTravelInfo(getTravelInfo().copy(dateOfJourney = value.trim()))
    }

    fun getDepartureTime(): String = prefs.getString(KEY_DEPARTURE_TIME, "") ?: ""
    fun setDepartureTime(value: String) {
        prefs.edit().putString(KEY_DEPARTURE_TIME, value.trim()).apply()
        saveTravelInfo(getTravelInfo().copy(departureTime = value.trim()))
    }

    fun getArrivalTime(): String = prefs.getString(KEY_ARRIVAL_TIME, "") ?: ""
    fun setArrivalTime(value: String) {
        prefs.edit().putString(KEY_ARRIVAL_TIME, value.trim()).apply()
        saveTravelInfo(getTravelInfo().copy(arrivalTime = value.trim()))
    }

    fun getBoardingStation(): String = prefs.getString(KEY_BOARDING_STATION, "") ?: ""
    fun setBoardingStation(value: String) {
        prefs.edit().putString(KEY_BOARDING_STATION, value.trim()).apply()
        saveTravelInfo(getTravelInfo().copy(boardingStation = value.trim()))
    }

    fun getDeboardingStation(): String = prefs.getString(KEY_DEBOARDING_STATION, "") ?: ""
    fun setDeboardingStation(value: String) {
        prefs.edit().putString(KEY_DEBOARDING_STATION, value.trim()).apply()
        saveTravelInfo(getTravelInfo().copy(deboardingStation = value.trim()))
    }

    companion object {
        private const val PREFS_NAME = "glanceables_prefs"
        private const val KEY_API_KEY = "openweather_api_key"
        private const val KEY_LATITUDE = "last_latitude"
        private const val KEY_LONGITUDE = "last_longitude"
        private const val KEY_WEATHER_DATA = "cached_weather_data"
        private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 30L

        // Local storage keys for travel variables extracted from PDF
        const val KEY_SEAT_NUMBER = "seat_number"
        const val KEY_BIRTHTYPE = "birthtype"
        const val KEY_DATE_OF_JOURNEY = "date_of_journey"
        const val KEY_DEPARTURE_TIME = "departure_time"
        const val KEY_ARRIVAL_TIME = "arrival_time"
        const val KEY_BOARDING_STATION = "boarding_station"
        const val KEY_DEBOARDING_STATION = "deboarding_station"
        private const val KEY_TRAVEL_INFO = "travel_info_data"
    }
}
