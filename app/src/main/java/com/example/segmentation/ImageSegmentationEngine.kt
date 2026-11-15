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
 * Combines MediaPipe object and landscape segmentation with relative depth.
 */
object ImageSegmentationEngine {
    private const val TAG = "SegmentationEngine"

    /** Set to null to leave unrecognized scenery stable instead of using ADE20K. */
    @Volatile
    var landscapeSegmenter: LandscapeSegmenter? = MediaPipeLandscapeSegmenter

    suspend fun processImage(
        context: Context,
        original: Bitmap,
        sceneName: String = "Live Photo Scene"
    ): ProcessedScene = withContext(Dispatchers.Default) {
        val srcW = max(1, original.width)
        val srcH = max(1, original.height)

        val scale = min(1f, min(540f / srcW, 1080f / srcH))
        val targetWidth = max(1, (srcW * scale).toInt())
        val targetHeight = max(1, (srcH * scale).toInt())
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
            Log.e(TAG, "MediaPipe object segmentation failed; using landscape labels only", e)
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "MediaPipe object runtime unavailable; using landscape labels only", e)
            null
        }

        val landscapeMask = try {
            landscapeSegmenter?.segment(context.applicationContext, scaled)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Landscape model failed; leaving unrecognized scenery stable", e)
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "Landscape model runtime unavailable; leaving unrecognized scenery stable", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Landscape model exhausted memory; leaving unrecognized scenery stable", e)
            null
        }

        val classMask = ByteArray(totalPixels)
        val swayMask = BooleanArray(totalPixels)

        // Unknown classes and unavailable models remain stable rather than inventing water or sky.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val objectClass = foregroundMask?.classAt(x, y, w, h) ?: 0
                val semanticClass = landscapeMask?.classAt(x, y, w, h) ?: 0
                classMask[idx] = layerForPixel(objectClass, semanticClass).ordinal.toByte()
                swayMask[idx] = isSwayablePixel(objectClass, semanticClass)
            }
        }

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

            val vegetation = if (type == LayerType.FOREGROUND && swayMask.any { it }) {
                val maskPixels = IntArray(totalPixels) { i ->
                    if (swayMask[i] && classMask[i].toInt() == classId) Color.WHITE else Color.TRANSPARENT
                }
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                    setPixels(maskPixels, 0, w, 0, 0, w, h)
                }
            } else null

            layers.add(
                ParsedLayer(
                    type = type,
                    bitmap = layerBmp,
                    depth = if (count > 0) (depthSum / count).toFloat() else type.defaultDepth,
                    coveragePercent = coverage,
                    pixelCount = count,
                    isEnabled = true,
                    swayMask = vegetation
                )
            )
        }

        // Pass 4: Build depth heatmap
        val depthBmp = generateDepthHeatmap(depthValues, w, h)
        val analysisMethod = listOf(
            if (foregroundMask != null) "MediaPipe objects" else "foreground model unavailable",
            if (landscapeMask != null) "ADE20K landscape" else "landscape model unavailable (stable fallback)",
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
