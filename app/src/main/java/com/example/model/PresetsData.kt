package com.example.model

/**
 * 12 offline motion presets for instantaneous shader parameter switching.
 */
object PresetsData {

    val presets: List<MotionPreset> = listOf(
        MotionPreset(
            id = "calm_horizon",
            name = "Calm Horizon",
            description = "Gentle cloud drift, calm glassy water, and subtle tree sway with comfortable depth.",
            iconName = "WbSunny",
            config = ShaderConfig(
                cloudDriftSpeed = 0.025f,
                waterRippleSpeed = 1.0f,
                waterRippleAmplitude = 0.012f,
                foregroundSwaySpeed = 1.0f,
                foregroundSwayAmplitude = 0.008f,
                parallaxDegrees = 3.5f,
                particleType = ParticleType.FLOATING_DUST,
                amoledMode = false
            )
        ),
        MotionPreset(
            id = "storm_surge",
            name = "Storm Surge",
            description = "Fast dark clouds, turbulent churning waves, wind-whipped foreground and rain streaks.",
            iconName = "Thunderstorm",
            config = ShaderConfig(
                cloudDriftSpeed = 0.08f,
                waterRippleSpeed = 2.8f,
                waterRippleAmplitude = 0.035f,
                waterWaveFrequency = 32.0f,
                foregroundSwaySpeed = 2.8f,
                foregroundSwayAmplitude = 0.03f,
                parallaxDegrees = 4.0f,
                particleType = ParticleType.RAIN_DROPS,
                amoledMode = false
            )
        ),
        MotionPreset(
            id = "sunset_dream",
            name = "Sunset Dream",
            description = "Golden hour light shift, rhythmic wave motion, warm glow and drifting petals.",
            iconName = "BrightnessMedium",
            config = ShaderConfig(
                cloudDriftSpeed = 0.02f,
                waterRippleSpeed = 1.2f,
                waterRippleAmplitude = 0.018f,
                foregroundSwaySpeed = 1.2f,
                foregroundSwayAmplitude = 0.012f,
                parallaxDegrees = 3.8f,
                particleType = ParticleType.CHERRY_PETALS,
                celestialSunTracking = true
            )
        ),
        MotionPreset(
            id = "cyber_neon",
            name = "Cyber Neon",
            description = "High-contrast AMOLED true black with high ripple reactivity and glowing fireflies.",
            iconName = "Bolt",
            config = ShaderConfig(
                cloudDriftSpeed = 0.04f,
                waterRippleSpeed = 2.0f,
                waterRippleAmplitude = 0.025f,
                foregroundSwaySpeed = 1.5f,
                foregroundSwayAmplitude = 0.015f,
                parallaxDegrees = 4.0f,
                particleType = ParticleType.FIREFLIES,
                amoledMode = true,
                amoledThreshold = 0.08f,
                touchRippleIntensity = 1.6f
            )
        ),
        MotionPreset(
            id = "misty_dawn",
            name = "Misty Dawn",
            description = "Atmospheric haze over still water with whispering wind sway and soft particle mist.",
            iconName = "CloudQueue",
            config = ShaderConfig(
                cloudDriftSpeed = 0.015f,
                waterRippleSpeed = 0.8f,
                waterRippleAmplitude = 0.008f,
                foregroundSwaySpeed = 0.9f,
                foregroundSwayAmplitude = 0.006f,
                parallaxDegrees = 2.5f,
                particleType = ParticleType.FLOATING_DUST,
                skyLightShift = 0.15f
            )
        ),
        MotionPreset(
            id = "frozen_aurora",
            name = "Frozen Aurora",
            description = "Ethereal sky illumination, crystalline water refraction, and drifting snow flurries.",
            iconName = "AcUnit",
            config = ShaderConfig(
                cloudDriftSpeed = 0.03f,
                waterRippleSpeed = 0.7f,
                waterRippleAmplitude = 0.01f,
                foregroundSwaySpeed = 0.8f,
                foregroundSwayAmplitude = 0.008f,
                parallaxDegrees = 4.0f,
                particleType = ParticleType.SNOW_FLAKES,
                skyLightShift = 0.35f
            )
        ),
        MotionPreset(
            id = "deep_parallax",
            name = "Deep Parallax",
            description = "Maximum 3D perspective separation between layers tracking gyroscope tilt at 4 degrees.",
            iconName = "ThreeDRotation",
            config = ShaderConfig(
                cloudDriftSpeed = 0.02f,
                waterRippleSpeed = 1.0f,
                waterRippleAmplitude = 0.012f,
                foregroundSwaySpeed = 1.0f,
                foregroundSwayAmplitude = 0.01f,
                parallaxDegrees = 4.0f,
                gyroscopeSensitivity = 1.8f,
                particleType = ParticleType.FLOATING_DUST
            )
        ),
        MotionPreset(
            id = "zen_garden",
            name = "Zen Garden",
            description = "Ultra battery-saver profile with capped 30 fps, peaceful ripples, and gentle motion.",
            iconName = "Spa",
            config = ShaderConfig(
                cloudDriftSpeed = 0.012f,
                waterRippleSpeed = 0.6f,
                waterRippleAmplitude = 0.007f,
                foregroundSwaySpeed = 0.6f,
                foregroundSwayAmplitude = 0.005f,
                parallaxDegrees = 2.0f,
                targetFps = 30,
                particleType = ParticleType.NONE
            )
        ),
        MotionPreset(
            id = "summer_meadow",
            name = "Summer Meadow",
            description = "Sun-drenched landscape with fluttering foliage and energetic pollen fireflies.",
            iconName = "Park",
            config = ShaderConfig(
                cloudDriftSpeed = 0.045f,
                waterRippleSpeed = 1.4f,
                waterRippleAmplitude = 0.015f,
                foregroundSwaySpeed = 2.0f,
                foregroundSwayAmplitude = 0.02f,
                parallaxDegrees = 3.6f,
                particleType = ParticleType.FIREFLIES
            )
        ),
        MotionPreset(
            id = "rainy_window",
            name = "Rainy Window",
            description = "Constant rain streaks, concentrated ripple propagation, and cooler atmospheric tint.",
            iconName = "WaterDrop",
            config = ShaderConfig(
                cloudDriftSpeed = 0.05f,
                waterRippleSpeed = 2.2f,
                waterRippleAmplitude = 0.028f,
                foregroundSwaySpeed = 1.8f,
                foregroundSwayAmplitude = 0.022f,
                parallaxDegrees = 3.2f,
                particleType = ParticleType.RAIN_DROPS,
                batterySaverCoolPalette = true
            )
        ),
        MotionPreset(
            id = "night_sky_glow",
            name = "Night Sky Glow",
            description = "Dark celestial mood with low brightness, AMOLED power savings, and soft fireflies.",
            iconName = "NightsStay",
            config = ShaderConfig(
                cloudDriftSpeed = 0.018f,
                waterRippleSpeed = 0.9f,
                waterRippleAmplitude = 0.01f,
                foregroundSwaySpeed = 0.8f,
                foregroundSwayAmplitude = 0.007f,
                parallaxDegrees = 3.0f,
                particleType = ParticleType.FIREFLIES,
                amoledMode = true,
                amoledThreshold = 0.06f
            )
        ),
        MotionPreset(
            id = "audio_pulse",
            name = "Audio Pulse",
            description = "High-responsiveness mode reacting to ambient music, speech, and screen tap shockwaves.",
            iconName = "GraphicEq",
            config = ShaderConfig(
                cloudDriftSpeed = 0.03f,
                waterRippleSpeed = 1.6f,
                waterRippleAmplitude = 0.025f,
                foregroundSwaySpeed = 1.8f,
                foregroundSwayAmplitude = 0.02f,
                parallaxDegrees = 4.0f,
                touchRippleIntensity = 2.0f,
                particleType = ParticleType.FIREFLIES,
                audioReactive = true,
                audioSensitivity = 1.5f
            )
        )
    )

    fun defaultPreset(): MotionPreset = presets[0]

    fun findById(id: String): MotionPreset = presets.firstOrNull { it.id == id } ?: defaultPreset()
}
