package com.example.segmentation

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter

internal object MediaPipeLandscapeSegmenter : LandscapeSegmenter {
    override fun segment(context: Context, bitmap: Bitmap): LandscapeMask {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("ade20k_deeplabv3.tflite")
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()

        ImageSegmenter.createFromOptions(context, options).use { segmenter ->
            val image = BitmapImageBuilder(bitmap).build()
            try {
                val mask = segmenter.segment(image).categoryMask()
                    .orElseThrow { IllegalStateException("MediaPipe returned no ADE20K category mask") }
                try {
                    val buffer = ByteBufferExtractor.extract(mask)
                    val pixels = ByteArray(mask.width * mask.height)
                    require(buffer.capacity() >= pixels.size) { "ADE20K category mask has an invalid size" }
                    for (i in pixels.indices) pixels[i] = buffer.get(i)
                    return LandscapeMask(pixels, mask.width, mask.height)
                } finally {
                    mask.close()
                }
            } finally {
                image.close()
            }
        }
    }
}
