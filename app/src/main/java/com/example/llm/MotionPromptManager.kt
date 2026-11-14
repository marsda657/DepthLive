package com.example.llm

import android.util.Log
import com.example.model.ParticleType
import com.example.model.PresetsData
import com.example.model.ShaderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class PromptResult(
    val config: ShaderConfig,
    val summary: String,
    val matchedPresetName: String
)

/**
 * Converts user natural language motion prompts ("light snow, slow zoom", "stormy ocean rain")
 * into precise GL shader and parallax parameter presets.
 */
object MotionPromptManager {
    private const val TAG = "MotionPromptManager"

    suspend fun parseMotionPrompt(
        promptText: String,
        baseConfig: ShaderConfig,
        aiSelector: (suspend (String) -> String)? = null
    ): PromptResult = withContext(Dispatchers.Default) {
        val lower = promptText.lowercase(Locale.ROOT).trim()
        var fallbackReason: String? = null

        if (aiSelector != null) {
            try {
                val selection = withTimeoutOrNull(12_000L) { aiSelector(promptText.take(500)) }
                val preset = PresetsData.presets.firstOrNull { it.id == selection?.trim() }
                if (preset != null) {
                    return@withContext PromptResult(
                        config = applyMotionPreset(baseConfig, preset.config),
                        summary = "AI selected ${preset.name}",
                        matchedPresetName = preset.name
                    )
                }
                fallbackReason = if (selection == null) "AI request timed out" else "AI returned an invalid preset"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AI motion prompt failed; using offline rules", e)
                fallbackReason = "AI unavailable"
            }
        }

        // 1. Check if matches one of the 12 offline presets directly
        for (preset in PresetsData.presets) {
            if (lower.contains(preset.name.lowercase(Locale.ROOT)) ||
                lower.contains(preset.id.replace("_", " "))
            ) {
                return@withContext PromptResult(
                    config = applyMotionPreset(baseConfig, preset.config),
                    summary = listOfNotNull(fallbackReason, "Offline preset: ${preset.name}").joinToString(" — "),
                    matchedPresetName = preset.name
                )
            }
        }

        // 2. Intelligent semantic rule-based keyword & parameter extractor
        var cloudSpeed = baseConfig.cloudDriftSpeed
        var waterSpeed = baseConfig.waterRippleSpeed
        var waterAmp = baseConfig.waterRippleAmplitude
        var swaySpeed = baseConfig.foregroundSwaySpeed
        var swayAmp = baseConfig.foregroundSwayAmplitude
        var parallax = baseConfig.parallaxDegrees
        var particle = baseConfig.particleType
        var amoled = baseConfig.amoledMode
        var sunTracking = baseConfig.celestialSunTracking
        var coolPalette = baseConfig.batterySaverCoolPalette

        val tags = mutableListOf<String>()

        // Speed analysis
        when {
            lower.contains("fast") || lower.contains("rapid") || lower.contains("turbulent") || lower.contains("high speed") -> {
                cloudSpeed *= 1.8f
                waterSpeed *= 1.8f
                swaySpeed *= 1.6f
                tags.add("High Velocity")
            }
            lower.contains("slow") || lower.contains("gentle") || lower.contains("peaceful") || lower.contains("calm") -> {
                cloudSpeed *= 0.5f
                waterSpeed *= 0.6f
                swaySpeed *= 0.5f
                swayAmp *= 0.6f
                tags.add("Tranquil Pace")
            }
        }

        // Rain & Storm
        if (lower.contains("rain") || lower.contains("drizzle") || lower.contains("downpour") || lower.contains("storm")) {
            particle = ParticleType.RAIN_DROPS
            waterSpeed = 2.4f
            waterAmp = 0.032f
            coolPalette = true
            tags.add("Rain Simulation")
        }

        // Snow & Winter
        if (lower.contains("snow") || lower.contains("blizzard") || lower.contains("frozen") || lower.contains("ice")) {
            particle = ParticleType.SNOW_FLAKES
            swaySpeed = 0.8f
            waterSpeed = 0.7f
            waterAmp = 0.008f
            tags.add("Snow Flurries")
        }

        // Wind & Sway
        if (lower.contains("wind") || lower.contains("sway") || lower.contains("breeze") || lower.contains("gust")) {
            swayAmp = 0.025f
            swaySpeed = 2.0f
            tags.add("Foliage Flutter")
        }

        // Parallax & Depth
        if (lower.contains("depth") || lower.contains("parallax") || lower.contains("3d") || lower.contains("tilt")) {
            parallax = 4.0f
            tags.add("Expanded 3D Parallax")
        }

        // AMOLED & Dark
        if (lower.contains("amoled") || lower.contains("dark") || lower.contains("black") || lower.contains("oled") || lower.contains("cyber")) {
            amoled = true
            particle = if (particle == ParticleType.NONE) ParticleType.FIREFLIES else particle
            tags.add("AMOLED True-Black Cutoff")
        }

        // Sunset & Golden Hour
        if (lower.contains("sunset") || lower.contains("sunrise") || lower.contains("golden") || lower.contains("dusk")) {
            sunTracking = true
            particle = ParticleType.CHERRY_PETALS
            tags.add("Golden Hour Bloom")
        }

        // Microphone activation requires the explicit permission flow in settings.
        if (lower.contains("audio") || lower.contains("music") || lower.contains("sound") || lower.contains("beat")) {
            tags.add("Enable audio in settings to react to sound")
        }

        // Floating dust / fireflies
        if (lower.contains("firefly") || lower.contains("glow") || lower.contains("sparkle")) {
            particle = ParticleType.FIREFLIES
            tags.add("Bioluminescent Fireflies")
        }

        val generatedConfig = baseConfig.copy(
            cloudDriftSpeed = cloudSpeed.coerceIn(0.005f, 0.12f),
            waterRippleSpeed = waterSpeed.coerceIn(0.3f, 3.5f),
            waterRippleAmplitude = waterAmp.coerceIn(0.004f, 0.05f),
            foregroundSwaySpeed = swaySpeed.coerceIn(0.4f, 3.5f),
            foregroundSwayAmplitude = swayAmp.coerceIn(0.003f, 0.04f),
            parallaxDegrees = parallax.coerceIn(0.5f, 4.0f),
            particleType = particle,
            amoledMode = amoled,
            celestialSunTracking = sunTracking,
            batterySaverCoolPalette = coolPalette
        )

        val desc = if (tags.isEmpty()) "Offline rules applied" else tags.joinToString(" • ")

        PromptResult(
            config = generatedConfig,
            summary = listOfNotNull(fallbackReason, desc).joinToString(" — "),
            matchedPresetName = "Custom Dynamic Preset"
        )
    }

    private fun applyMotionPreset(base: ShaderConfig, motion: ShaderConfig): ShaderConfig = base.copy(
        cloudDriftSpeed = motion.cloudDriftSpeed,
        skyLightShift = motion.skyLightShift,
        waterRippleSpeed = motion.waterRippleSpeed,
        waterRippleAmplitude = motion.waterRippleAmplitude,
        waterWaveFrequency = motion.waterWaveFrequency,
        causticShimmer = motion.causticShimmer,
        foregroundSwaySpeed = motion.foregroundSwaySpeed,
        foregroundSwayAmplitude = motion.foregroundSwayAmplitude,
        parallaxDegrees = motion.parallaxDegrees,
        touchRippleIntensity = motion.touchRippleIntensity,
        particleType = motion.particleType
    )
}
