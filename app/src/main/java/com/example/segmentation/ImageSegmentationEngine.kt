package com.example.segmentation

import android.graphics.Bitmap
import android.graphics.Color
import android.content.Context
import android.util.Log
import com.example.model.LayerType
import com.example.model.ParsedLayer
import com.example.model.ProcessedScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Combines MediaPipe object segmentation, approximate landscape layers and relative depth.
 */
object ImageSegmentationEngine {
    private const val TAG = "SegmentationEngine"

    /** Set to null to use heuristic landscape layers instead of ADE20K. */
    @Volatile
    var landscapeSegmenter: LandscapeSegmenter? = MediaPipeLandscapeSegmenter

    suspend fun processImage(
        context: Context,
        original: Bitmap,
        sceneName: String = "Live Photo Scene"
    ): ProcessedScene = withContext(Dispatchers.Default) {
        val srcW = max(1, original.width)
        val srcH = max(1, original.height)

        // Downsample to 540px width for fast, reliable, crash-free mobile processing
        val targetWidth = 540
        val targetHeight = max(1, (srcH * (targetWidth.toFloat() / srcW)).toInt())
        val resized = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        val scaled = if (resized.config == Bitmap.Config.ARGB_8888) resized
            else resized.copy(Bitmap.Config.ARGB_8888, false)

        val w = scaled.width
        val h = scaled.height
        val totalPixels = w * h

        val pixels = IntArray(totalPixels)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val foregroundMask = try {
            MediaPipeForegroundSegmenter.segment(context.applicationContext, scaled)
        } catch (e: RuntimeException) {
            Log.e(TAG, "MediaPipe segmentation failed; using heuristic layers", e)
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "MediaPipe native runtime unavailable; using heuristic layers", e)
            null
        }

        val landscapeMask = try {
            landscapeSegmenter?.segment(context.applicationContext, scaled)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Landscape model failed; using heuristic layers", e)
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "Landscape model runtime unavailable; using heuristic layers", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Landscape model exhausted memory; using heuristic layers", e)
            null
        }

        val classMask = ByteArray(totalPixels)
        val hsv = FloatArray(3)

        // Pass 1: Semantic feature extraction and boundary estimation
        for (y in 0 until h) {
            val yRatio = y.toFloat() / h
            for (x in 0 until w) {
                val idx = y * w + x
                val c = pixels[idx]
                Color.colorToHSV(c, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                val isBlueHue = hue in 170.0f..255.0f
                val isCyanHue = hue in 150.0f..190.0f
                val isGreenHue = hue in 60.0f..160.0f
                val isEarthTone = (hue in 20.0f..65.0f && sat > 0.20f) || (value < 0.25f)

                // Foreground subject detection: prominent vertical structure with high contrast / foliage
                val isForegroundCandidate = if (x > w * 0.45f && yRatio in 0.08f..0.98f) {
                    (isGreenHue && sat > 0.18f && value in 0.10f..0.85f) ||
                            (isEarthTone && sat > 0.18f && value in 0.12f..0.65f)
                } else if (yRatio > 0.88f) {
                    true
                } else {
                    false
                }
                val landscapeLayer = landscapeMask?.layerAt(x, y, w, h)

                if (foregroundMask?.contains(x, y, w, h) ?: isForegroundCandidate) {
                    classMask[idx] = 4 // FOREGROUND
                } else if (landscapeLayer != null) {
                    classMask[idx] = when (landscapeLayer) {
                        LayerType.SKY -> 0
                        LayerType.BACKGROUND -> 1
                        LayerType.WATER -> 2
                        LayerType.GROUND -> 3
                        LayerType.FOREGROUND -> 4
                    }.toByte()
                } else if (yRatio < 0.38f && (isBlueHue || (value > 0.70f && sat < 0.28f))) {
                    classMask[idx] = 0 // SKY
                } else if (yRatio in 0.32f..0.54f && (value < 0.65f || hue in 200.0f..280.0f || (sat < 0.28f && value > 0.35f))) {
                    classMask[idx] = 1 // BACKGROUND
                } else if (yRatio in 0.44f..0.82f && (isBlueHue || isCyanHue || (sat < 0.40f && value in 0.20f..0.75f))) {
                    classMask[idx] = 2 // WATER
                } else {
                    classMask[idx] = 3 // GROUND
                }
            }
        }

        // Pass 2: Spatial smoothing with safe bounds check
        smoothClassificationMask(classMask, w, h)

        val estimatedDepth = try {
            MidasDepthEstimator.estimate(context.applicationContext, scaled).resize(w, h)
        } catch (e: IOException) {
            Log.e(TAG, "Depth model unavailable; using layer depth estimates", e)
            null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Depth estimation failed; using layer depth estimates", e)
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "Depth runtime unavailable; using layer depth estimates", e)
            null
        }
        val fallbackDepths = floatArrayOf(0.05f, 0.25f, 0.45f, 0.65f, 0.95f)
        val depthValues = estimatedDepth ?: FloatArray(totalPixels) { fallbackDepths[classMask[it].toInt()] }

        // Pass 3: Extract individual layer bitmaps
        val layers = mutableListOf<ParsedLayer>()
        val layerTypes = listOf(
            LayerType.SKY to 0,
            LayerType.BACKGROUND to 1,
            LayerType.WATER to 2,
            LayerType.GROUND to 3,
            LayerType.FOREGROUND to 4
        )

        for ((type, classId) in layerTypes) {
            val layerPixels = IntArray(totalPixels)
            var count = 0
            var depthSum = 0.0

            for (i in 0 until totalPixels) {
                if (classMask[i].toInt() == classId) {
                    layerPixels[i] = pixels[i]
                    count++
                    depthSum += depthValues[i]
                } else {
                    layerPixels[i] = 0x00000000 // transparent
                }
            }

            val layerBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            layerBmp.setPixels(layerPixels, 0, w, 0, 0, w, h)

            val coverage = (count.toFloat() / totalPixels) * 100f

            layers.add(
                ParsedLayer(
                    type = type,
                    bitmap = layerBmp,
                    depth = if (count > 0) (depthSum / count).toFloat() else type.defaultDepth,
                    coveragePercent = coverage,
                    pixelCount = count,
                    isEnabled = true
                )
            )
        }

        // Pass 4: Build depth heatmap
        val depthBmp = generateDepthHeatmap(depthValues, w, h)
        val analysisMethod = listOf(
            if (foregroundMask != null) "MediaPipe objects" else "heuristic foreground",
            if (landscapeMask != null) "ADE20K landscape" else "heuristic landscape",
            if (estimatedDepth != null) "MiDaS relative depth" else "heuristic depth fallback"
        ).joinToString("; ")

        ProcessedScene(
            id = UUID.randomUUID().toString(),
            name = sceneName,
            originalBitmap = scaled,
            depthMapBitmap = depthBmp,
            layers = layers,
            width = w,
            height = h,
            analysisMethod = analysisMethod
        )
    }

    private fun smoothClassificationMask(mask: ByteArray, w: Int, h: Int) {
        val copy = mask.clone()
        val counts = IntArray(5)

        for (y in 1 until h - 2 step 2) {
            for (x in 1 until w - 2 step 2) {
                counts.fill(0)
                for (dy in -1..1) {
                    val py = y + dy
                    if (py !in 0 until h) continue
                    for (dx in -1..1) {
                        val px = x + dx
                        if (px !in 0 until w) continue
                        val c = copy[py * w + px].toInt()
                        if (c in 0..4) counts[c]++
                    }
                }
                var maxClass = 0
                var maxCount = 0
                for (c in 0..4) {
                    if (counts[c] > maxCount) {
                        maxCount = counts[c]
                        maxClass = c
                    }
                }
                if (maxCount >= 5) {
                    val b = maxClass.toByte()
                    val p0 = y * w + x
                    val p1 = y * w + (x + 1)
                    val p2 = (y + 1) * w + x
                    val p3 = (y + 1) * w + (x + 1)
                    if (p0 in mask.indices) mask[p0] = b
                    if (p1 in mask.indices) mask[p1] = b
                    if (p2 in mask.indices) mask[p2] = b
                    if (p3 in mask.indices) mask[p3] = b
                }
            }
        }
    }

    private fun generateDepthHeatmap(depthMap: FloatArray, w: Int, h: Int): Bitmap {
        val outPixels = IntArray(w * h)
        for (i in depthMap.indices) {
            val d = max(0f, min(1f, depthMap[i]))
            outPixels[i] = turboColor(d)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(outPixels, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun turboColor(t: Float): Int {
        val r = (255 * min(1f, max(0f, 1.5f * (t - 0.25f)))).toInt()
        val g = (255 * min(1f, max(0f, 1.0f - abs(t - 0.5f) * 2.0f))).toInt()
        val b = (255 * min(1f, max(0f, 1.5f * (0.75f - t)))).toInt()
        return Color.rgb(r, g, b)
    }
}
