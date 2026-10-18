package com.meilluer.glanceables.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.meilluer.glanceables.data.WeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends weather data to connected Wear OS devices via the Wearable Data Layer API.
 *
 * The watch app reads from the path [DATA_PATH] to update its complication.
 */
class WearableDataSender(private val context: Context) {

    companion object {
        private const val TAG = "WearableDataSender"
        const val DATA_PATH = "/weather_data"

        // Data map keys
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_FEELS_LIKE = "feels_like"
        const val KEY_HUMIDITY = "humidity"
        const val KEY_UV_INDEX = "uv_index"
        const val KEY_AQI = "aqi"
        const val KEY_AQI_LABEL = "aqi_label"
        const val KEY_PM25 = "pm25"
        const val KEY_CITY_NAME = "city_name"
        const val KEY_LAST_UPDATED = "last_updated"
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
}
