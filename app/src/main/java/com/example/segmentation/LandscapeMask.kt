package com.example.segmentation

import com.example.model.LayerType

/** Runs a model that labels pixels with 1-based ADE20K-150 indices (0 = unlabeled). Returns null if unavailable. */
fun interface LandscapeSegmenter {
    fun segment(context: android.content.Context, bitmap: android.graphics.Bitmap): LandscapeMask?
}

/** Maps ADE20K-150 indices (objectInfo150.csv) to wallpaper layers; unmapped classes return null. */
internal object Ade20kLayers {
    private val map: Map<Int, LayerType> = buildMap {
        put(3, LayerType.SKY)
        listOf(22, 27, 61, 110, 114, 129).forEach { put(it, LayerType.WATER) }
        listOf(14, 10, 30, 47, 95, 35, 53, 7, 12).forEach { put(it, LayerType.GROUND) } // earth, grass, field, sand, land, rock, path, road, sidewalk
        listOf(17, 69, 2, 26, 49, 85, 62).forEach { put(it, LayerType.BACKGROUND) } // mountain, hill, building, house, skyscraper, tower, bridge
        listOf(13, 5, 18, 73, 67, 21, 77, 81, 84, 91, 103, 104, 117, 127, 128)
            .forEach { put(it, LayerType.FOREGROUND) }
    }

    fun layerFor(index: Int): LayerType? = map[index]
    fun isVegetation(index: Int): Boolean = index == 5 || index == 18 || index == 67 || index == 73
}

class LandscapeMask(val pixels: ByteArray, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height)
    }

    fun layerAt(x: Int, y: Int, imageWidth: Int, imageHeight: Int): LayerType? {
        return Ade20kLayers.layerFor(classAt(x, y, imageWidth, imageHeight))
    }

    internal fun classAt(x: Int, y: Int, imageWidth: Int, imageHeight: Int): Int {
        require(imageWidth > 0 && imageHeight > 0)
        val mx = (x.toLong() * width / imageWidth).toInt().coerceIn(0, width - 1)
        val my = (y.toLong() * height / imageHeight).toInt().coerceIn(0, height - 1)
        return pixels[my * width + mx].toInt() and 0xff
    }
}
