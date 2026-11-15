package com.example.segmentation

import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

internal fun ownedMediaPipeImage(bitmap: Bitmap): MPImage {
    // Closing a bitmap-backed MPImage recycles its bitmap, so never give it the caller's bitmap.
    val copy = checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) {
        "Could not copy bitmap for MediaPipe"
    }
    return BitmapImageBuilder(copy).build()
}
