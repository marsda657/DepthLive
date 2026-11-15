package com.example.weather

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenMeteoClientTest {
    @Test
    fun usesOnlyConfiguredCityAndItsResolvedCoordinates() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val queries = mutableListOf<String>()
        server.createContext("/search") { exchange ->
            queries.add(exchange.requestURI.rawQuery)
            val body = """{"results":[{"name":"São Paulo","country":"Brazil","latitude":-23.55,"longitude":-46.63}]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/forecast") { exchange ->
            queries.add(exchange.requestURI.rawQuery)
            val body = """{"current":{"weather_code":61,"temperature_2m":18.5}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val result = OpenMeteoClient("$base/search", "$base/forecast").fetch("São Paulo") { true }
            assertEquals("São Paulo, Brazil", result.resolvedName)
            assertTrue(result.weather.isRaining)
            assertEquals("name=S%C3%A3o+Paulo&count=1&format=json", queries[0])
            assertTrue(queries[1].contains("latitude=-23.55&longitude=-46.63"))
        } finally {
            server.stop(0)
        }
    }
}
