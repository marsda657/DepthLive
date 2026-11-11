package com.example.model

import android.graphics.Bitmap

/**
 * The 5 semantic depth layers for the live wallpaper.
 */
enum class LayerType(
    val title: String,
    val defaultDepth: Float, // 0.0 (farthest, sky) to 1.0 (nearest, foreground)
    val colorHex: Long,
    val description: String
) {
    SKY("Sky & Atmosphere", 0.05f, 0xFF4FC3F7, "Cloud drift, celestial sun angle, and atmospheric light"),
    BACKGROUND("Distant Horizon", 0.25f, 0xFF7986CB, "Mountain range, distant skyline, and horizon parallax"),
    WATER("Water & Reflection", 0.45f, 0xFF29B6F6, "Sine-wave ripples, caustic highlights, and reflections"),
    GROUND("Terrain & Midground", 0.65f, 0xFF81C784, "Shoreline, hills, ground plane, and perspective stability"),
    FOREGROUND("Foreground Subject", 0.95f, 0xFFFFB74D, "Foliage, trees, figures, and wind-sway deformation")
}

/**
 * A segmented layer extracted from the user's photo.
 */
data class ParsedLayer(
    val type: LayerType,
    val bitmap: Bitmap,
    val depth: Float,
    val coveragePercent: Float,
    val pixelCount: Int,
    val isEnabled: Boolean = true
)

/**
 * Full processed scene containing the original image, depth map, and individual layers.
 */
data class ProcessedScene(
    val id: String,
    val name: String,
    val originalBitmap: Bitmap,
    val depthMapBitmap: Bitmap,
    val layers: List<ParsedLayer>,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of ambient particles that can drift or scatter on unlock.
 */
enum class ParticleType(val label: String) {
    FLOATING_DUST("Luminous Dust"),
    FIREFLIES("Glowing Fireflies"),
    CHERRY_PETALS("Blossom Petals"),
    RAIN_DROPS("Rain Streaks"),
    SNOW_FLAKES("Drifting Snow"),
    NONE("Disabled")
}

/**
 * Live shader and motion configuration parameters.
 */
data class ShaderConfig(
    // Sky Shader
    val cloudDriftSpeed: Float = 0.035f,
    val celestialSunTracking: Boolean = true,
    val skyLightShift: Float = 0.2f,

    // Water Shader
    val waterRippleSpeed: Float = 1.3f,
    val waterRippleAmplitude: Float = 0.016f,
    val waterWaveFrequency: Float = 24.0f,
    val causticShimmer: Float = 0.35f,

    // Foreground Shader
    val foregroundSwaySpeed: Float = 1.4f,
    val foregroundSwayAmplitude: Float = 0.012f,

    // Parallax & Sensors
    val parallaxDegrees: Float = 4.0f, // up to 4 degrees parallax
    val gyroscopeSensitivity: Float = 1.0f,
    val homeScreenScrollParallax: Boolean = true,

    // Touch Ripple
    val touchRippleIntensity: Float = 1.0f,
    val touchRippleDurationMs: Long = 1800L,

    // Unlock & Wake Event
    val unlockScatterDurationMs: Long = 800L,
    val unlockParticleCount: Int = 45,
    val particleType: ParticleType = ParticleType.FLOATING_DUST,

    // AMOLED Mode & Power
    val amoledMode: Boolean = false,
    val amoledThreshold: Float = 0.05f, // pixels darker than this turn black RGB(0,0,0)
    val batterySaverCoolPalette: Boolean = true, // shift cooler under 20% battery
    val targetFps: Int = 60, // 60, 30, 15

    // Weather & Ambient
    val weatherReactivity: Boolean = true,
    val autoWeatherOverlays: Boolean = true,

    // Audio-reactive
    val audioReactive: Boolean = false,
    val audioSensitivity: Float = 1.0f
)

/**
 * Motion Preset definition with predefined shader parameters.
 */
data class MotionPreset(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val config: ShaderConfig
)
