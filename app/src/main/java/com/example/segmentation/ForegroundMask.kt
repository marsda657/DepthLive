package com.example.segmentation

internal data class ForegroundMask(val pixels: ByteArray, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height)
    }

    fun contains(x: Int, y: Int, imageWidth: Int, imageHeight: Int): Boolean {
        require(imageWidth > 0 && imageHeight > 0)
        val maskX = (x.toLong() * width / imageWidth).toInt().coerceIn(0, width - 1)
        val maskY = (y.toLong() * height / imageHeight).toInt().coerceIn(0, height - 1)
        return (pixels[maskY * width + maskX].toInt() and 0xff) in 1..20
    }
}
