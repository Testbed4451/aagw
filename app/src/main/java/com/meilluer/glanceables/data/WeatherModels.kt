package com.meilluer.glanceables.data

import com.google.gson.annotations.SerializedName

// ── Current Weather Response ──────────────────────────────────────────────────
data class WeatherResponse(
    @SerializedName("main") val main: MainWeather,
    @SerializedName("coord") val coord: Coord,
    @SerializedName("name") val cityName: String,
    @SerializedName("dt") val timestamp: Long
)

data class MainWeather(
    @SerializedName("temp") val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    @SerializedName("humidity") val humidity: Int
)

data class Coord(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double
)

// ── UV Index Response (2.5 endpoint) ──────────────────────────────────────────
data class UvIndexResponse(
    @SerializedName("value") val value: Double,
    @SerializedName("date_iso") val dateIso: String?
)

// ── Air Pollution Response ────────────────────────────────────────────────────
data class AirPollutionResponse(
    @SerializedName("list") val list: List<AirPollutionEntry>
)

data class AirPollutionEntry(
    @SerializedName("main") val main: AqiMain,
    @SerializedName("components") val components: AirComponents,
    @SerializedName("dt") val timestamp: Long
)

data class AqiMain(
    @SerializedName("aqi") val aqi: Int // 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
)

data class AirComponents(
    @SerializedName("pm2_5") val pm25: Double,
    @SerializedName("pm10") val pm10: Double,
    @SerializedName("no2") val no2: Double,
    @SerializedName("o3") val o3: Double
)

// ── Local weather data model (stored + sent to watch) ─────────────────────────
data class WeatherData(
    val temperature: Double = 0.0,       // Celsius
    val feelsLike: Double = 0.0,
    val humidity: Int = 0,
    val uvIndex: Double = 0.0,
    val aqi: Int = 0,                    // 1-5
    val aqiLabel: String = "N/A",
    val pm25: Double = 0.0,
    val cityName: String = "",
    val lastUpdated: Long = 0L           // epoch millis
) {
    companion object {
        fun aqiToLabel(aqi: Int): String = when (aqi) {
            1 -> "Good"
            2 -> "Fair"
            3 -> "Moderate"
            4 -> "Poor"
            5 -> "Very Poor"
            else -> "N/A"
        }
    }
}
