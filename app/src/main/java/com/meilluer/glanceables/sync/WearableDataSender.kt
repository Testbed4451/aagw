package com.meilluer.glanceables.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.meilluer.glanceables.data.TravelInfo
import com.meilluer.glanceables.data.WeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends weather data and travel info to connected Wear OS devices via the Wearable Data Layer API.
 */
class WearableDataSender(private val context: Context) {

    companion object {
        private const val TAG = "WearableDataSender"
        const val DATA_PATH = "/weather_data"
        const val TRAVEL_DATA_PATH = "/travel_data"

        // Weather Data map keys
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_FEELS_LIKE = "feels_like"
        const val KEY_HUMIDITY = "humidity"
        const val KEY_UV_INDEX = "uv_index"
        const val KEY_AQI = "aqi"
        const val KEY_AQI_LABEL = "aqi_label"
        const val KEY_PM25 = "pm25"
        const val KEY_CITY_NAME = "city_name"
        const val KEY_LAST_UPDATED = "last_updated"

        // Travel Data map keys
        const val KEY_TRAVEL_SEAT_NUMBER = "seat_number"
        const val KEY_TRAVEL_BIRTHTYPE = "birthtype"
        const val KEY_TRAVEL_DATE_OF_JOURNEY = "date_of_journey"
        const val KEY_TRAVEL_DEPARTURE_TIME = "departure_time"
        const val KEY_TRAVEL_ARRIVAL_TIME = "arrival_time"
        const val KEY_TRAVEL_BOARDING_STATION = "boarding_station"
        const val KEY_TRAVEL_DEBOARDING_STATION = "deboarding_station"
        const val KEY_TRAVEL_LAST_UPDATED = "last_updated"
    }

    /**
     * Push [weatherData] to all connected Wear OS devices.
     */
    suspend fun sendToWatch(weatherData: WeatherData) {
        withContext(Dispatchers.IO) {
            try {
                val putDataMapRequest = PutDataMapRequest.create(DATA_PATH).apply {
                    dataMap.putDouble(KEY_TEMPERATURE, weatherData.temperature)
                    dataMap.putDouble(KEY_FEELS_LIKE, weatherData.feelsLike)
                    dataMap.putInt(KEY_HUMIDITY, weatherData.humidity)
                    dataMap.putDouble(KEY_UV_INDEX, weatherData.uvIndex)
                    dataMap.putInt(KEY_AQI, weatherData.aqi)
                    dataMap.putString(KEY_AQI_LABEL, weatherData.aqiLabel)
                    dataMap.putDouble(KEY_PM25, weatherData.pm25)
                    dataMap.putString(KEY_CITY_NAME, weatherData.cityName)
                    dataMap.putLong(KEY_LAST_UPDATED, weatherData.lastUpdated)
                }

                val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()
                val dataClient = Wearable.getDataClient(context)
                Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d(TAG, "Weather data sent to watch successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send weather data to watch", e)
            }
        }
    }

    /**
     * Push [travelInfo] to all connected Wear OS devices.
     */
    suspend fun sendTravelInfoToWatch(travelInfo: TravelInfo) {
        withContext(Dispatchers.IO) {
            try {
                val putDataMapRequest = PutDataMapRequest.create(TRAVEL_DATA_PATH).apply {
                    dataMap.putString(KEY_TRAVEL_SEAT_NUMBER, travelInfo.seatNumber)
                    dataMap.putString(KEY_TRAVEL_BIRTHTYPE, travelInfo.birthtype)
                    dataMap.putString(KEY_TRAVEL_DATE_OF_JOURNEY, travelInfo.dateOfJourney)
                    dataMap.putString(KEY_TRAVEL_DEPARTURE_TIME, travelInfo.departureTime)
                    dataMap.putString(KEY_TRAVEL_ARRIVAL_TIME, travelInfo.arrivalTime)
                    dataMap.putString(KEY_TRAVEL_BOARDING_STATION, travelInfo.boardingStation)
                    dataMap.putString(KEY_TRAVEL_DEBOARDING_STATION, travelInfo.deboardingStation)
                    dataMap.putLong(KEY_TRAVEL_LAST_UPDATED, System.currentTimeMillis())
                }

                val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()
                val dataClient = Wearable.getDataClient(context)
                Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d(TAG, "Travel data sent to watch successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send travel data to watch", e)
            }
        }
    }
}
