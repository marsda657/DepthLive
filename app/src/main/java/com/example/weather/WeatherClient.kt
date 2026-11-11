package com.example.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CurrentWeather(
    val weatherCode: Int,
    val temperatureC: Float,
    val conditionDescription: String,
    val isRaining: Boolean,
    val isSnowing: Boolean,
    val isCloudy: Boolean
)

/**
 * Open-Meteo live weather client for ambient wallpaper reactivity.
 * Free open API with zero key requirement and graceful offline fallbacks.
 */
object WeatherClient {
    private const val TAG = "WeatherClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeather(lat: Double = 37.7749, lon: Double = -122.4194): CurrentWeather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code,temperature_2m"
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val current = json.getJSONObject("current")
                val code = current.optInt("weather_code", 0)
                val temp = current.optDouble("temperature_2m", 20.0).toFloat()
                return@withContext parseWeatherCode(code, temp)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch live weather: ${e.message}, using clear ambient fallback")
        }

        // Fallback default
        CurrentWeather(
            weatherCode = 0,
            temperatureC = 21.0f,
            conditionDescription = "Clear Skies",
            isRaining = false,
            isSnowing = false,
            isCloudy = false
        )
    }

    fun parseWeatherCode(code: Int, temp: Float): CurrentWeather {
        val isRain = code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99)
        val isSnow = code in listOf(71, 73, 75, 77, 85, 86)
        val isCloud = code in listOf(1, 2, 3, 45, 48)

        val desc = when (code) {
            0 -> "Clear Skies"
            1, 2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Misty Fog"
            51, 53, 55 -> "Light Drizzle"
            61, 63, 65 -> "Rain Showers"
            71, 73, 75 -> "Snowfall"
            80, 81, 82 -> "Heavy Rain"
            95, 96, 99 -> "Thunderstorm"
            else -> "Fair"
        }

        return CurrentWeather(
            weatherCode = code,
            temperatureC = temp,
            conditionDescription = desc,
            isRaining = isRain,
            isSnowing = isSnow,
            isCloudy = isCloud
        )
    }
}
