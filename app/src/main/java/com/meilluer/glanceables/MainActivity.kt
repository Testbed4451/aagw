package com.meilluer.glanceables

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.meilluer.glanceables.data.WeatherData
import com.meilluer.glanceables.databinding.ActivityMainBinding
import com.meilluer.glanceables.repository.WeatherRepository
import com.meilluer.glanceables.storage.PreferencesManager
import com.meilluer.glanceables.sync.WearableDataSender
import com.meilluer.glanceables.sync.WeatherSyncWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private val repository = WeatherRepository()
    private lateinit var wearableSender: WearableDataSender

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        wearableSender = WearableDataSender(this)

        setupUi()
        loadCachedData()
    }

    private fun setupUi() {
        // Restore saved API key
        val savedKey = prefs.getApiKey()
        if (savedKey.isNotBlank()) {
            binding.editApiKey.setText(savedKey)
        }

        // Save API key button
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.editApiKey.text.toString().trim()
            if (key.isBlank()) {
                binding.apiKeyInputLayout.error = "API key is required"
                return@setOnClickListener
            }
            binding.apiKeyInputLayout.error = null
            prefs.setApiKey(key)
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            schedulePeriodicSync()
        }

        // Refresh button
        binding.btnRefresh.setOnClickListener {
            fetchWeatherWithLocation()
        }

        // Swipe-to-refresh
        binding.swipeRefresh.setOnRefreshListener {
            fetchWeatherWithLocation()
        }
    }

    private fun loadCachedData() {
        val cached = prefs.getCachedWeatherData()
        if (cached != null) {
            displayWeatherData(cached)
        }
    }

    private fun fetchWeatherWithLocation() {
        if (!prefs.hasApiKey()) {
            Toast.makeText(this, "Please enter and save your API key first", Toast.LENGTH_LONG).show()
            binding.swipeRefresh.isRefreshing = false
            return
        }

        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
            binding.swipeRefresh.isRefreshing = false
            return
        }

        setLoading(true)
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                prefs.setLocation(location.latitude, location.longitude)
                fetchWeatherData(location.latitude, location.longitude)
            } else {
                // Fall back to last known location
                val lat = prefs.getLatitude()
                val lon = prefs.getLongitude()
                if (lat != 0.0 || lon != 0.0) {
                    fetchWeatherData(lat, lon)
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener { e ->
            setLoading(false)
            Toast.makeText(this, "Location error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        val apiKey = prefs.getApiKey()

        lifecycleScope.launch {
            try {
                val weatherData = repository.fetchWeatherData(lat, lon, apiKey)
                prefs.setCachedWeatherData(weatherData)
                displayWeatherData(weatherData)

                // Push to watch
                wearableSender.sendToWatch(weatherData)

                // Ensure periodic sync is running
                schedulePeriodicSync()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun displayWeatherData(data: WeatherData) {
        binding.apply {
            cardWeather.visibility = View.VISIBLE

            // City
            textCity.text = data.cityName.ifBlank { "Unknown" }

            // Temperature
            textTemperature.text = String.format(Locale.getDefault(), "%.1f°C", data.temperature)
            textFeelsLike.text = String.format(
                Locale.getDefault(), "Feels like %.1f°C  •  Humidity %d%%",
                data.feelsLike, data.humidity
            )

            // UV Index
            textUvIndex.text = String.format(Locale.getDefault(), "%.1f", data.uvIndex)
            textUvLabel.text = uvIndexLabel(data.uvIndex)

            // AQI
            textAqi.text = data.aqi.toString()
            textAqiLabel.text = data.aqiLabel
            textPm25.text = String.format(Locale.getDefault(), "PM2.5: %.1f µg/m³", data.pm25)

            // Last updated
            if (data.lastUpdated > 0) {
                val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                textLastUpdated.text = "Updated: ${sdf.format(Date(data.lastUpdated))}"
                textLastUpdated.visibility = View.VISIBLE
            }
        }
    }

    private fun uvIndexLabel(uv: Double): String = when {
        uv <= 2 -> "Low"
        uv <= 5 -> "Moderate"
        uv <= 7 -> "High"
        uv <= 10 -> "Very High"
        else -> "Extreme"
    }

    private fun setLoading(loading: Boolean) {
        binding.swipeRefresh.isRefreshing = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
            30, TimeUnit.MINUTES  // minimum interval for periodic work
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeatherSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchWeatherWithLocation()
            } else {
                Toast.makeText(this, "Location permission required for weather data", Toast.LENGTH_LONG).show()
            }
        }
    }
}
