package com.example.gl

import com.example.model.LayerType

internal data class HitLayer(val type: LayerType, val depth: Float)
internal data class TexturePoint(val x: Float, val y: Float)

internal fun centerCropScale(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): TexturePoint {
    val imageAspect = imageWidth.toFloat() / imageHeight.coerceAtLeast(1)
    val viewAspect = viewWidth.toFloat() / viewHeight.coerceAtLeast(1)
    return if (imageAspect > viewAspect) TexturePoint(viewAspect / imageAspect, 1f)
    else TexturePoint(1f, imageAspect / viewAspect)
}

internal fun texturePoint(
    x: Float, y: Float, depth: Float, tiltX: Float, tiltY: Float,
    parallaxScale: Float, homeScroll: Float, crop: TexturePoint
): TexturePoint {
    val factor = depth - 0.5f
    val dx = tiltX * factor * parallaxScale - homeScroll * factor * 0.12f
    val dy = tiltY * factor * parallaxScale
    return TexturePoint((x - dx / 2f - 0.5f) * crop.x + 0.5f, (y + dy / 2f - 0.5f) * crop.y + 0.5f)
}

internal fun visibleWaterAt(
    x: Float, y: Float, layers: List<HitLayer>,
    tiltX: Float, tiltY: Float, parallaxScale: Float, homeScroll: Float,
    crop: TexturePoint,
    isOpaque: (HitLayer, TexturePoint) -> Boolean
): TexturePoint? {
    if (x !in 0f..1f || y !in 0f..1f) return null
    for (layer in layers.sortedBy { it.type.ordinal }.asReversed()) {
        val point = texturePoint(x, y, layer.depth, tiltX, tiltY, parallaxScale, homeScroll, crop)
        if (point.x !in 0f..1f || point.y !in 0f..1f || !isOpaque(layer, point)) continue
        return if (layer.type == LayerType.WATER) point else null
    }
    return null
}
