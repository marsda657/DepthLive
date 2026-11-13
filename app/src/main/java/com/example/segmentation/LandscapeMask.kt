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
        listOf(22, 27, 61, 114, 129).forEach { put(it, LayerType.WATER) } // water, sea, river, waterfall, lake
        listOf(14, 10, 30, 47, 95, 35, 53, 7, 12).forEach { put(it, LayerType.GROUND) } // earth, grass, field, sand, land, rock, path, road, sidewalk
        listOf(17, 69, 2, 26, 49, 85, 62).forEach { put(it, LayerType.BACKGROUND) } // mountain, hill, building, house, skyscraper, tower, bridge
        listOf(13, 5, 18, 73, 67, 77).forEach { put(it, LayerType.FOREGROUND) } // person, tree, plant, palm, flower, boat
    }

    fun layerFor(index: Int): LayerType? = map[index]
}

class LandscapeMask(val pixels: ByteArray, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height)
    }

    fun layerAt(x: Int, y: Int, imageWidth: Int, imageHeight: Int): LayerType? {
        val mx = (x.toLong() * width / imageWidth).toInt().coerceIn(0, width - 1)
        val my = (y.toLong() * height / imageHeight).toInt().coerceIn(0, height - 1)
        return Ade20kLayers.layerFor(pixels[my * width + mx].toInt() and 0xff)
    }
}
