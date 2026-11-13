package com.example.weather

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherSnapshot(
    val city: String,
    val resolvedName: String,
    val weather: CurrentWeather,
    val fetchedAt: Long
)

data class WeatherState(
    val city: String = "",
    val forecast: WeatherSnapshot? = null,
    val refreshing: Boolean = false,
    val error: String? = null
)

internal interface WeatherStore {
    fun load(): WeatherState
    fun saveCity(city: String)
    fun saveForecast(snapshot: WeatherSnapshot)
}

internal class WeatherController(
    private val store: WeatherStore,
    private val fetch: suspend (String, () -> Boolean) -> WeatherSnapshot,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onFailure: (Exception) -> Unit = {
        Log.w("WeatherRepository", "Weather refresh failed; retaining cached forecast", it)
    }
) {
    companion object {
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private var generation = 0L

    @Synchronized
    fun setCity(value: String) {
        val city = value.trim()
        require(city.isEmpty() || city.length in 2..100) { "Enter a city with 2 to 100 characters" }
        if (city == _state.value.city) return
        generation++
        store.saveCity(city)
        _state.value = WeatherState(city = city)
    }

    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val start = synchronized(this) {
            val current = _state.value
            if (current.city.isEmpty() || (!force && current.forecast != null &&
                    clock() - current.forecast.fetchedAt in 0 until REFRESH_INTERVAL_MS)) {
                null
            } else {
                _state.value = current.copy(refreshing = true, error = null)
                current to generation
            }
        } ?: return@withLock
        val (initial, token) = start
        try {
            val result = fetch(initial.city) { synchronized(this) { generation == token } }
            synchronized(this) {
                if (generation == token && result.city == initial.city) {
                    store.saveForecast(result)
                    _state.value = WeatherState(city = initial.city, forecast = result)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            fail(token, e)
        } catch (e: JSONException) {
            fail(token, e)
        } catch (e: IllegalArgumentException) {
            fail(token, e)
        } finally {
            synchronized(this) {
                if (generation == token && _state.value.refreshing) {
                    _state.value = _state.value.copy(refreshing = false)
                }
            }
        }
    }

    @Synchronized
    private fun fail(token: Long, error: Exception) {
        onFailure(error)
        if (generation == token) {
            _state.value = _state.value.copy(refreshing = false, error = "Weather unavailable; using cached forecast if available.")
        }
    }
}

object WeatherRepository {
    private var controller: WeatherController? = null
    val state: StateFlow<WeatherState> get() = requireNotNull(controller).state

    @Synchronized
    fun init(context: Context) {
        if (controller != null) return
        val prefs = context.applicationContext.getSharedPreferences("depthlive_weather", Context.MODE_PRIVATE)
        val store = object : WeatherStore {
            override fun load(): WeatherState {
                val city = prefs.getString("city", "").orEmpty()
                val cachedCity = prefs.getString("cachedCity", "").orEmpty()
                val forecast = if (city.isNotEmpty() && city == cachedCity && prefs.contains("code")) {
                    WeatherSnapshot(
                        city, prefs.getString("resolvedName", city).orEmpty(),
                        WeatherClient.parseWeatherCode(prefs.getInt("code", 0), prefs.getFloat("temperature", 0f)),
                        prefs.getLong("fetchedAt", 0L)
                    )
                } else null
                return WeatherState(city, forecast)
            }

            override fun saveCity(city: String) {
                prefs.edit().putString("city", city).remove("cachedCity").remove("code")
                    .remove("temperature").remove("resolvedName").remove("fetchedAt").apply()
            }

            override fun saveForecast(snapshot: WeatherSnapshot) {
                prefs.edit().putString("cachedCity", snapshot.city)
                    .putString("resolvedName", snapshot.resolvedName)
                    .putInt("code", snapshot.weather.weatherCode)
                    .putFloat("temperature", snapshot.weather.temperatureC)
                    .putLong("fetchedAt", snapshot.fetchedAt).apply()
            }
        }
        controller = WeatherController(store, OpenMeteoClient()::fetch)
    }

    fun setCity(city: String) = requireNotNull(controller).setCity(city)
    suspend fun refresh(force: Boolean = false) = requireNotNull(controller).refresh(force)
    const val REFRESH_INTERVAL_MS = WeatherController.REFRESH_INTERVAL_MS
}

internal class OpenMeteoClient(
    private val geocodingEndpoint: String = "https://geocoding-api.open-meteo.com/v1/search",
    private val forecastEndpoint: String = "https://api.open-meteo.com/v1/forecast"
) {
    suspend fun fetch(city: String, isCurrent: () -> Boolean): WeatherSnapshot = withContext(Dispatchers.IO) {
        require(city.length in 2..100)
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("City changed")
        val query = URLEncoder.encode(city, "UTF-8")
        val places = getJson("$geocodingEndpoint?name=$query&count=1&format=json")
            .optJSONArray("results")
        require(places != null && places.length() > 0) { "City not found" }
        val place = places.getJSONObject(0)
        val latitude = place.getDouble("latitude")
        val longitude = place.getDouble("longitude")
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("City changed")
        val current = getJson(
            "$forecastEndpoint?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code&forecast_days=1"
        ).getJSONObject("current")
        val temperature = current.getDouble("temperature_2m")
        require(temperature.isFinite())
        val name = listOf(place.getString("name"), place.optString("admin1"), place.optString("country"))
            .filter { it.isNotBlank() }.distinct().joinToString(", ")
        WeatherSnapshot(city, name, WeatherClient.parseWeatherCode(current.getInt("weather_code"), temperature.toFloat()), System.currentTimeMillis())
    }

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            if (connection.responseCode !in 200..299) throw IOException("Open-Meteo HTTP ${connection.responseCode}")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
