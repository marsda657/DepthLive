package com.example.weather

data class CurrentWeather(
    val weatherCode: Int,
    val temperatureC: Float,
    val conditionDescription: String,
    val isRaining: Boolean,
    val isSnowing: Boolean,
    val isCloudy: Boolean
)

/**
 * Classification of Open-Meteo weather codes.
 */
object WeatherClient {
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
