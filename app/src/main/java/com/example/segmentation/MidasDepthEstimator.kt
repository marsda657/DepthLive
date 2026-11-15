package com.example.segmentation

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal object MidasDepthEstimator {
    private const val SIZE = 256

    fun estimate(context: Context, bitmap: Bitmap): RelativeDepth {
        val model = context.assets.openFd("midas_v21_small.tflite").use { asset ->
            FileInputStream(asset.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
            }
        }
        val inputBitmap = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        try {
            inputBitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        } finally {
            if (inputBitmap !== bitmap) inputBitmap.recycle()
        }
        val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val mean = floatArrayOf(123.675f, 116.28f, 103.53f)
        val std = floatArrayOf(58.395f, 57.12f, 57.375f)
        for (pixel in pixels) {
            input.putFloat(((pixel shr 16 and 255) - mean[0]) / std[0])
            input.putFloat(((pixel shr 8 and 255) - mean[1]) / std[1])
            input.putFloat(((pixel and 255) - mean[2]) / std[2])
        }
        input.rewind()
        val output = ByteBuffer.allocateDirect(SIZE * SIZE * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        Interpreter(model, Interpreter.Options().setNumThreads(2)).use { interpreter ->
            require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, SIZE, SIZE, 3)))
            require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, SIZE, SIZE, 1)))
            require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
            require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
            interpreter.run(input, output)
        }
        output.rewind()
        return RelativeDepth.normalize(FloatArray(SIZE * SIZE) { output.float }, SIZE, SIZE)
    }
}
