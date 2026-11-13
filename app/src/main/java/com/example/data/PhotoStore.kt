package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException

internal class PhotoStore(private val context: Context) {
    val photoFile = File(context.filesDir, "active_wallpaper_photo.jpg")

    fun copyFrom(uri: Uri): Bitmap {
        val temporary = File.createTempFile("wallpaper-photo-", ".tmp", context.filesDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected photo")
            input.use { source ->
                temporary.outputStream().use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }
            val bitmap = decode(temporary)
            if (!temporary.renameTo(photoFile)) {
                throw IOException("Could not save the selected photo")
            }
            return bitmap
        } finally {
            temporary.delete()
        }
    }

    fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Could not decode the selected photo")
        }
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, options)
            ?: throw IOException("Could not decode the selected photo")
    }

    internal companion object {
        fun sampleSize(imageWidth: Int, imageHeight: Int, screenWidth: Int, screenHeight: Int): Int {
            require(imageWidth > 0 && imageHeight > 0 && screenWidth > 0 && screenHeight > 0)
            var sample = 1
            while (imageWidth / sample > screenWidth || imageHeight / sample > screenHeight) {
                sample *= 2
            }
            return sample
        }
    }
}
