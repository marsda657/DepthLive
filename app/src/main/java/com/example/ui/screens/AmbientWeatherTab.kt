package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WallpaperRepository
import com.example.model.ParticleType
import com.example.weather.WeatherRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Ambient settings for local time, manual overlays, and battery behavior.
 */
@Composable
fun AmbientWeatherTab(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val config by WallpaperRepository.shaderConfig.collectAsState()
    val weather by WeatherRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var cityInput by remember(weather.city) { mutableStateOf(weather.city) }

    // Read real battery status from Android system
    var deviceBatteryPercent by remember { mutableIntStateOf(100) }

    LaunchedEffect(Unit) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            deviceBatteryPercent = (level * 100) / scale
        }

    }

    // Calculate solar elevation angle
    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val totalMinutes = hour * 60 + minute
    val solarPhase = when (hour) {
        in 5..7 -> "Sunrise / Dawn Bloom"
        in 8..16 -> "Full Daylight"
        in 17..19 -> "Golden Hour Sunset"
        in 20..22 -> "Twilight Dusk"
        else -> "Midnight Celestial Glow"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Ambient & Environmental Reactivity",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Local time, optional city weather, and battery settings.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )

        // Manual overlays do not require a network request or location.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF1E293B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Weather overlays",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Optional Open-Meteo weather for a city you choose.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                }

                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it.take(100) },
                    label = { Text("City (optionally include region or country)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("weather_city_input")
                )
                Text(
                    "Saving sends this city to Open-Meteo. The first matching city is used; no device location is accessed.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val changed = cityInput.trim() != weather.city
                            WeatherRepository.setCity(cityInput)
                            scope.launch { WeatherRepository.refresh(force = !changed) }
                        },
                        enabled = cityInput.trim().length in 2..100 && !weather.refreshing,
                        modifier = Modifier.testTag("weather_city_save")
                    ) { Text("Save city") }
                    OutlinedButton(
                        onClick = { WeatherRepository.setCity(""); cityInput = "" },
                        enabled = weather.city.isNotEmpty(),
                        modifier = Modifier.testTag("weather_city_clear")
                    ) { Text("Disable weather") }
                }
                val forecast = weather.forecast
                Text(
                    text = when {
                        weather.refreshing -> "Updating weather..."
                        weather.error != null -> weather.error!!
                        forecast != null -> "${forecast.resolvedName}: ${forecast.weather.conditionDescription} (${forecast.weather.temperatureC}°C)"
                        weather.city.isEmpty() -> "Weather disabled. Time and battery settings remain active."
                        else -> "No forecast available yet."
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.testTag("weather_status")
                )
                if (weather.error != null && forecast != null) {
                    Text(
                        "Last successful: ${forecast.resolvedName}, ${forecast.weather.conditionDescription}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                if (forecast != null) {
                    Text(
                        "Last updated: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(forecast.fetchedAt))}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Text("Simulate Weather Overlays", fontSize = 12.sp, color = Color(0xFF94A3B8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = config.particleType == ParticleType.RAIN_DROPS,
                        onClick = {
                            val newType = if (config.particleType == ParticleType.RAIN_DROPS) ParticleType.NONE else ParticleType.RAIN_DROPS
                            WallpaperRepository.updateConfig(config.copy(particleType = newType))
                        },
                        label = { Text("Force Rain", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8),
                            selectedLabelColor = Color(0xFF032840)
                        )
                    )
                    FilterChip(
                        selected = config.particleType == ParticleType.SNOW_FLAKES,
                        onClick = {
                            val newType = if (config.particleType == ParticleType.SNOW_FLAKES) ParticleType.NONE else ParticleType.SNOW_FLAKES
                            WallpaperRepository.updateConfig(config.copy(particleType = newType))
                        },
                        label = { Text("Force Snow", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFA855F7),
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = config.particleType == ParticleType.FIREFLIES,
                        onClick = {
                            val newType = if (config.particleType == ParticleType.FIREFLIES) ParticleType.NONE else ParticleType.FIREFLIES
                            WallpaperRepository.updateConfig(config.copy(particleType = newType))
                        },
                        label = { Text("Fireflies", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // 2. CELESTIAL SUN POSITION & SYSTEM CLOCK CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFFBBF24))
                    Text(
                        text = "Celestial 24h Sun Tracking",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = solarPhase,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "Local Time: ${String.format("%02d:%02d", hour, minute)}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Text(
                        text = "Azimuth ${(totalMinutes * 360 / 1440)}°",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Battery status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (deviceBatteryPercent < 20) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (deviceBatteryPercent < 20) Color(0xFFF43F5E) else Color(0xFF10B981)
                    )
                    Text(
                        text = "Battery Status",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "Rendering and sensors pause while the wallpaper is hidden or the device is in Doze. Battery usage depends on the device and needs measurement.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current Battery Level",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$deviceBatteryPercent%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deviceBatteryPercent < 20) Color(0xFFF43F5E) else Color(0xFF10B981)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Power Policy",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = if (deviceBatteryPercent < 20) "30 FPS Cap" else "Configured frame rate",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
