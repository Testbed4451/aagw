package com.meilluer.glanceables.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.meilluer.glanceables.data.FootballMatch
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
        const val KEY_TRAVEL_TRAIN_NUMBER = "train_number"
        const val KEY_TRAVEL_TRAIN_NAME = "train_name"
        const val KEY_TRAVEL_LAST_UPDATED = "last_updated"

        // Football Data map keys
        const val FOOTBALL_DATA_PATH = "/football_data"
        const val KEY_FOOTBALL_HOME_TEAM = "football_home_team"
        const val KEY_FOOTBALL_AWAY_TEAM = "football_away_team"
        const val KEY_FOOTBALL_HOME_SCORE = "football_home_score"
        const val KEY_FOOTBALL_AWAY_SCORE = "football_away_score"
        const val KEY_FOOTBALL_STATUS = "football_status"
        const val KEY_FOOTBALL_TIME = "football_time"
        const val KEY_FOOTBALL_COMPETITION = "football_competition"
        const val KEY_FOOTBALL_IS_UPCOMING = "football_is_upcoming"
        const val KEY_FOOTBALL_SUMMARY = "football_summary"
        const val KEY_FOOTBALL_LAST_UPDATED = "football_last_updated"
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
                    dataMap.putString(KEY_TRAVEL_DATE_OF_JOURNEY, travelInfo.dateOfJourney ?: "")
                    dataMap.putString(KEY_TRAVEL_DEPARTURE_TIME, travelInfo.departureTime ?: "")
                    dataMap.putString(KEY_TRAVEL_ARRIVAL_TIME, travelInfo.arrivalTime)
                    dataMap.putString(KEY_TRAVEL_BOARDING_STATION, travelInfo.boardingStation)
                    dataMap.putString(KEY_TRAVEL_DEBOARDING_STATION, travelInfo.deboardingStation)
                    dataMap.putString(KEY_TRAVEL_TRAIN_NUMBER, travelInfo.trainNumber)
                    dataMap.putString(KEY_TRAVEL_TRAIN_NAME, travelInfo.trainName)
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

    /**
     * Push football match info to connected Wear OS devices for complication display.
     *
     * @param match The current live/scheduled match or next match day match.
     * @param isUpcoming True if no matches are on today and this is the next match day.
     */
    suspend fun sendFootballDataToWatch(match: FootballMatch, isUpcoming: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                val homeName = match.homeTeam.tla.ifBlank { match.homeTeam.shortName.ifBlank { match.homeTeam.name } }
                val awayName = match.awayTeam.tla.ifBlank { match.awayTeam.shortName.ifBlank { match.awayTeam.name } }
                val homeScore = match.score.fullTime.home ?: -1
                val awayScore = match.score.fullTime.away ?: -1

                val statusDisplay = when (match.status) {
                    "IN_PLAY" -> "LIVE"
                    "PAUSED" -> "HT"
                    "FINISHED" -> "FT"
                    "SCHEDULED", "TIMED" -> "SCH"
                    else -> match.status
                }

                val summary = if (homeScore >= 0 && awayScore >= 0) {
                    "$homeName $homeScore - $awayScore $awayName ($statusDisplay)"
                } else {
                    "$homeName vs $awayName ($statusDisplay)"
                }

                val putDataMapRequest = PutDataMapRequest.create(FOOTBALL_DATA_PATH).apply {
                    dataMap.putString(KEY_FOOTBALL_HOME_TEAM, homeName)
                    dataMap.putString(KEY_FOOTBALL_AWAY_TEAM, awayName)
                    dataMap.putInt(KEY_FOOTBALL_HOME_SCORE, homeScore)
                    dataMap.putInt(KEY_FOOTBALL_AWAY_SCORE, awayScore)
                    dataMap.putString(KEY_FOOTBALL_STATUS, statusDisplay)
                    dataMap.putString(KEY_FOOTBALL_TIME, match.utcDate)
                    dataMap.putString(KEY_FOOTBALL_COMPETITION, match.competition.name)
                    dataMap.putBoolean(KEY_FOOTBALL_IS_UPCOMING, isUpcoming)
                    dataMap.putString(KEY_FOOTBALL_SUMMARY, summary)
                    dataMap.putLong(KEY_FOOTBALL_LAST_UPDATED, System.currentTimeMillis())
                }

                val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()
                val dataClient = Wearable.getDataClient(context)
                Tasks.await(dataClient.putDataItem(putDataRequest))
                Log.d(TAG, "Football data sent to watch successfully: $summary")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send football data to watch", e)
            }
        }
    }
}
