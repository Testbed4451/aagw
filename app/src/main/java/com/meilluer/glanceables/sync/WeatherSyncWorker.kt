package com.meilluer.glanceables.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meilluer.glanceables.debug.AppLogger
import com.meilluer.glanceables.repository.FootballRepository
import com.meilluer.glanceables.repository.FootballResult
import com.meilluer.glanceables.repository.WeatherRepository
import com.meilluer.glanceables.storage.PreferencesManager

/**
 * WorkManager worker that periodically fetches weather data, caches it locally,
 * and pushes it to the connected watch via the Wearable Data Layer.
 */
class WeatherSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "WeatherSyncWorker"
        const val WORK_NAME = "weather_sync_periodic"
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)

        val apiKey = prefs.getApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "No API key configured, skipping sync")
            return Result.failure()
        }

        val lat = prefs.getLatitude()
        val lon = prefs.getLongitude()
        if (lat == 0.0 && lon == 0.0) {
            Log.w(TAG, "No location available, skipping sync")
            return Result.failure()
        }

        return try {
            val repository = WeatherRepository()
            val weatherData = repository.fetchWeatherData(lat, lon, apiKey)

            // Cache locally
            prefs.setCachedWeatherData(weatherData)

            // Send to watch
            val sender = WearableDataSender(applicationContext)
            sender.sendToWatch(weatherData)

            Log.d(TAG, "Weather sync completed: ${weatherData.cityName} ${weatherData.temperature}°C")
            AppLogger.i(TAG, "Background sync completed: ${weatherData.cityName} ${weatherData.temperature}°C")

            // Sync football scores if configured
            val footballApiKey = prefs.getFootballApiKey()
            val followedTeams = prefs.getFollowedTeams()
            if (footballApiKey.isNotBlank() && followedTeams.isNotEmpty()) {
                try {
                    val footballRepo = FootballRepository()
                    val result = footballRepo.getMatchesOrNextDay(
                        footballApiKey,
                        followedTeams.map { it.teamId }
                    )
                    when (result) {
                        is FootballResult.TodayMatches -> {
                            if (result.matches.isNotEmpty()) {
                                sender.sendFootballDataToWatch(result.matches.first(), isUpcoming = false)
                            }
                        }
                        is FootballResult.UpcomingMatch -> {
                            sender.sendFootballDataToWatch(result.match, isUpcoming = true)
                        }
                        is FootballResult.NoMatches -> {
                            // No action needed
                        }
                    }
                    Log.d(TAG, "Football sync completed in background")
                    AppLogger.i(TAG, "Background football sync completed: ${result.javaClass.simpleName}")
                } catch (e: Exception) {
                    Log.w(TAG, "Football sync failed in background", e)
                    AppLogger.w(TAG, "Background football sync failed: ${e.message}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weather sync failed", e)
            AppLogger.e(TAG, "Background sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
