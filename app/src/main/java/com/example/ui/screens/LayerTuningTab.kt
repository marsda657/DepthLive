package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.AudioReactiveManager
import com.example.data.WallpaperRepository
import com.example.model.ParticleType
import com.example.model.ShaderConfig

/**
 * Fine-tuning screen for configuring each shader layer, parallax depth, AMOLED, and audio reactivity.
 */
@Composable
fun LayerTuningTab(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val config by WallpaperRepository.shaderConfig.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            WallpaperRepository.updateConfig(config.copy(audioReactive = true))
            AudioReactiveManager.startListening(context)
            Toast.makeText(context, "Microphone audio-reactive mode enabled", Toast.LENGTH_SHORT).show()
        } else {
            WallpaperRepository.updateConfig(config.copy(audioReactive = false))
            Toast.makeText(context, "Audio permission is required for reactive mode", Toast.LENGTH_SHORT).show()
        }
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
            text = "Layer & Shader Customization",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Adjust live shader animations, parallax geometry, and ambient physics.",
            fontSize = 13.sp,
            color = Color(0xFF94A3B8)
        )

        // 1. SKY LAYER SHADER
        TuningCard(
            title = "Sky & Atmosphere Shader",
            subtitle = "Cloud drift UV shift, solar angle, and ambient lighting",
            icon = Icons.Default.Cloud,
            iconTint = Color(0xFF4FC3F7)
        ) {
            SliderRow(
                label = "Cloud Drift Speed",
                value = config.cloudDriftSpeed,
                valueRange = 0.0f..0.12f,
                displayValue = "${String.format("%.3f", config.cloudDriftSpeed)}x",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(cloudDriftSpeed = it)) }
            )

            ToggleRow(
                label = "Track Real Sun Angle (24h Clock)",
                description = "Shifts atmospheric light according to local solar elevation",
                checked = config.celestialSunTracking,
                onCheckedChange = { WallpaperRepository.updateConfig(config.copy(celestialSunTracking = it)) }
            )

            SliderRow(
                label = "Atmospheric Light Shift",
                value = config.skyLightShift,
                valueRange = 0.0f..0.5f,
                displayValue = "${String.format("%.2f", config.skyLightShift)}",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(skyLightShift = it)) }
            )
        }

        // 2. WATER LAYER SHADER
        TuningCard(
            title = "Water & Reflection Shader",
            subtitle = "Multi-frequency sine-wave ripples, displacement, and caustics",
            icon = Icons.Default.Water,
            iconTint = Color(0xFF29B6F6)
        ) {
            SliderRow(
                label = "Wave Ripple Speed",
                value = config.waterRippleSpeed,
                valueRange = 0.2f..3.5f,
                displayValue = "${String.format("%.1f", config.waterRippleSpeed)}x",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(waterRippleSpeed = it)) }
            )

            SliderRow(
                label = "Wave Amplitude (Height)",
                value = config.waterRippleAmplitude,
                valueRange = 0.005f..0.045f,
                displayValue = "${String.format("%.3f", config.waterRippleAmplitude)}",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(waterRippleAmplitude = it)) }
            )

            SliderRow(
                label = "Specular Caustic Shimmer",
                value = config.causticShimmer,
                valueRange = 0.0f..0.8f,
                displayValue = "${String.format("%.2f", config.causticShimmer)}",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(causticShimmer = it)) }
            )
        }

        // 3. FOREGROUND SUBJECT SHADER
        TuningCard(
            title = "Foreground Subject Shader",
            subtitle = "Wind sway deformation rooted at the base with upper flutter",
            icon = Icons.Default.Nature,
            iconTint = Color(0xFFFFB74D)
        ) {
            SliderRow(
                label = "Wind Sway Speed",
                value = config.foregroundSwaySpeed,
                valueRange = 0.3f..3.5f,
                displayValue = "${String.format("%.1f", config.foregroundSwaySpeed)}x",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(foregroundSwaySpeed = it)) }
            )

            SliderRow(
                label = "Wind Sway Amplitude",
                value = config.foregroundSwayAmplitude,
                valueRange = 0.002f..0.035f,
                displayValue = "${String.format("%.3f", config.foregroundSwayAmplitude)}",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(foregroundSwayAmplitude = it)) }
            )
        }

        // 4. PARALLAX & SENSORS
        TuningCard(
            title = "3D Gyroscope & Accelerometer Parallax",
            subtitle = "Perspective separation between 5 depth planes up to 4 degrees",
            icon = Icons.Default.ScreenRotation,
            iconTint = Color(0xFFA855F7)
        ) {
            SliderRow(
                label = "Parallax Tilt Limit",
                value = config.parallaxDegrees,
                valueRange = 0.5f..4.0f,
                displayValue = "${String.format("%.1f", config.parallaxDegrees)}°",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(parallaxDegrees = it)) }
            )

            SliderRow(
                label = "Gyroscope Sensitivity",
                value = config.gyroscopeSensitivity,
                valueRange = 0.2f..2.5f,
                displayValue = "${String.format("%.1f", config.gyroscopeSensitivity)}x",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(gyroscopeSensitivity = it)) }
            )

            ToggleRow(
                label = "Home Screen Panning Parallax",
                description = "Shifts depth planes as you swipe home screen pages",
                checked = config.homeScreenScrollParallax,
                onCheckedChange = { WallpaperRepository.updateConfig(config.copy(homeScreenScrollParallax = it)) }
            )
        }

        // 5. TOUCH RIPPLES
        TuningCard(
            title = "Touch Ripple Physics",
            subtitle = "Interactive shockwave propagating through layers by depth",
            icon = Icons.Default.TouchApp,
            iconTint = Color(0xFF10B981)
        ) {
            SliderRow(
                label = "Ripple Shockwave Intensity",
                value = config.touchRippleIntensity,
                valueRange = 0.0f..2.5f,
                displayValue = "${String.format("%.1f", config.touchRippleIntensity)}x",
                onValueChange = { WallpaperRepository.updateConfig(config.copy(touchRippleIntensity = it)) }
            )
        }

        // 6. AMBIENT PARTICLES
        TuningCard(
            title = "Ambient Particles & Weather Overlays",
            subtitle = "Particle effects drifting across depth layers",
            icon = Icons.Default.Nature,
            iconTint = Color(0xFF38BDF8)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Particle Mode", fontSize = 13.sp, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ParticleType.values().take(3).forEach { p ->
                        FilterChip(
                            selected = config.particleType == p,
                            onClick = { WallpaperRepository.updateConfig(config.copy(particleType = p)) },
                            label = { Text(p.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF38BDF8),
                                selectedLabelColor = Color(0xFF032840)
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ParticleType.values().drop(3).forEach { p ->
                        FilterChip(
                            selected = config.particleType == p,
                            onClick = { WallpaperRepository.updateConfig(config.copy(particleType = p)) },
                            label = { Text(p.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF38BDF8),
                                selectedLabelColor = Color(0xFF032840)
                            )
                        )
                    }
                }
            }
        }

        // 7. AMOLED & POWER SAVER
        TuningCard(
            title = "AMOLED & Battery Budget (<3%/day)",
            subtitle = "Darkens true-black regions to turn OLED pixels completely off",
            icon = Icons.Default.DarkMode,
            iconTint = Color(0xFFE2E8F0)
        ) {
            ToggleRow(
                label = "AMOLED True-Black Cutoff",
                description = "Forces dark shadows to pure RGB(0,0,0) for zero OLED power",
                checked = config.amoledMode,
                onCheckedChange = { WallpaperRepository.updateConfig(config.copy(amoledMode = it)) }
            )

            if (config.amoledMode) {
                SliderRow(
                    label = "Black Cutoff Luminance Threshold",
                    value = config.amoledThreshold,
                    valueRange = 0.01f..0.15f,
                    displayValue = "${(config.amoledThreshold * 100).toInt()}%",
                    onValueChange = { WallpaperRepository.updateConfig(config.copy(amoledThreshold = it)) }
                )
            }

            ToggleRow(
                label = "Battery Cooler Palette & Limiter (<20%)",
                description = "Shifts color tone cooler and caps frame rate to 30 fps under 20% battery",
                checked = config.batterySaverCoolPalette,
                onCheckedChange = { WallpaperRepository.updateConfig(config.copy(batterySaverCoolPalette = it)) }
            )

            // Target FPS Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target Frame Rate", fontSize = 13.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 30, 15).forEach { fps ->
                        FilterChip(
                            selected = config.targetFps == fps,
                            onClick = { WallpaperRepository.updateConfig(config.copy(targetFps = fps)) },
                            label = { Text("${fps} FPS", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 8. AUDIO REACTIVE MODE
        TuningCard(
            title = "Audio-Reactive Microphone Mode",
            subtitle = "Modulates ripples, sway, and sky glow with ambient sound & music",
            icon = Icons.Default.Mic,
            iconTint = Color(0xFFF43F5E)
        ) {
            ToggleRow(
                label = "Enable Audio Reactivity",
                description = "Requires microphone permission (no recordings stored)",
                checked = config.audioReactive,
                onCheckedChange = { enable ->
                    if (enable) {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            WallpaperRepository.updateConfig(config.copy(audioReactive = true))
                            AudioReactiveManager.startListening(context)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        WallpaperRepository.updateConfig(config.copy(audioReactive = false))
                        AudioReactiveManager.stopListening()
                    }
                }
            )

            if (config.audioReactive) {
                SliderRow(
                    label = "Audio Reactivity Sensitivity",
                    value = config.audioSensitivity,
                    valueRange = 0.5f..2.5f,
                    displayValue = "${String.format("%.1f", config.audioSensitivity)}x",
                    onValueChange = { WallpaperRepository.updateConfig(config.copy(audioSensitivity = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TuningCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            content()
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = Color(0xFFE2E8F0))
            Text(displayValue, fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8),
                inactiveTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF38BDF8),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}
