package com.example.segmentation

internal class RelativeDepth private constructor(
    private val values: FloatArray,
    private val width: Int,
    private val height: Int
) {
    fun resize(imageWidth: Int, imageHeight: Int): FloatArray {
        require(imageWidth > 0 && imageHeight > 0)
        return FloatArray(imageWidth * imageHeight) { index ->
            val x = ((index % imageWidth).toLong() * width / imageWidth).toInt()
            val y = ((index / imageWidth).toLong() * height / imageHeight).toInt()
            values[y * width + x]
        }
    }

    companion object {
        fun normalize(values: FloatArray, width: Int, height: Int): RelativeDepth {
            require(width > 0 && height > 0 && values.size.toLong() == width.toLong() * height)
            require(values.all { it.isFinite() }) { "Depth model returned non-finite values" }
            val min = values.minOrNull()!!
            val max = values.maxOrNull()!!
            val range = max - min
            require(range.isFinite() && range > 1e-6f) { "Depth model returned a flat map" }
            return RelativeDepth(FloatArray(values.size) { (values[it] - min) / range }, width, height)
        }
    }
}
