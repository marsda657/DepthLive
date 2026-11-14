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
        require(code in setOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)) {
            "Unknown Open-Meteo weather code: $code"
        }
        val isRain = code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        val isSnow = code in listOf(71, 73, 75, 77, 85, 86)
        val isCloud = code in listOf(1, 2, 3, 45, 48)

        val desc = when (code) {
            0 -> "Clear Skies"
            1, 2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Misty Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain Showers"
            71, 73, 75, 77, 85, 86 -> "Snowfall"
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
