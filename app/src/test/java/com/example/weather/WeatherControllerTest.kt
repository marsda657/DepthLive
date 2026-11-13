package com.example.weather

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WeatherControllerTest {
    private class Store : WeatherStore {
        var saved = WeatherState()
        override fun load() = saved
        override fun saveCity(city: String) { saved = WeatherState(city) }
        override fun saveForecast(snapshot: WeatherSnapshot) {
            saved = WeatherState(snapshot.city, snapshot)
        }
    }

    private fun forecast(city: String) = WeatherSnapshot(
        city, "$city, Example", WeatherClient.parseWeatherCode(61, 12f), 1_000L
    )

    @Test
    fun noCityNeverFetchesAndFreshForecastIsCached() = runBlocking {
        val store = Store()
        var calls = 0
        val controller = WeatherController(store, { city, _ -> calls++; forecast(city) }, { 1_001L })
        controller.refresh(force = true)
        assertEquals(0, calls)
        controller.setCity("Paris")
        controller.refresh()
        controller.refresh()
        assertEquals(1, calls)
        assertTrue(controller.state.value.forecast!!.weather.isRaining)
        assertEquals(controller.state.value.forecast, WeatherController(store, { _, _ -> error("unused") }).state.value.forecast)
        controller.setCity("")
        controller.refresh()
        assertEquals(1, calls)
        assertNull(controller.state.value.forecast)
    }

    @Test
    fun failureKeepsCachedForecastAndChangingCityClearsIt() = runBlocking {
        val store = Store()
        var failing = false
        val controller = WeatherController(store, { city, _ ->
            if (failing) throw IOException("offline")
            forecast(city)
        }, onFailure = {})
        controller.setCity("Paris")
        controller.refresh()
        failing = true
        controller.refresh(force = true)
        assertEquals("Paris", controller.state.value.forecast?.city)
        assertTrue(controller.state.value.error != null)
        controller.setCity("London")
        assertNull(controller.state.value.forecast)
        assertEquals("London", store.saved.city)
    }

    @Test
    fun staleResponseCannotOverwriteNewCity() = runBlocking {
        val start = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val store = Store()
        val controller = WeatherController(store, { city, _ ->
            start.complete(Unit)
            finish.await()
            forecast(city)
        })
        controller.setCity("Paris")
        val job = launch { controller.refresh() }
        start.await()
        controller.setCity("London")
        finish.complete(Unit)
        job.join()
        yield()
        assertEquals("London", controller.state.value.city)
        assertNull(controller.state.value.forecast)
        assertEquals("London", store.saved.city)
    }
}
