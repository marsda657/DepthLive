package com.example.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStoreTest {
    @Test
    fun copiesContentUriAndRestoresWithoutProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "picked-photo.jpg")
        context.assets.open("sample_landscape.jpg").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val expected = source.readBytes()
        TestPhotoProvider.source = source
        val provider = Robolectric.buildContentProvider(TestPhotoProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("test.photos", provider)

        val store = PhotoStore(context)
        store.copyFrom(Uri.parse("content://test.photos/photo"))
        assertTrue(source.delete())
        assertArrayEquals(expected, store.photoFile.readBytes())
        val restored = PhotoStore(context).decode(store.photoFile)
        assertTrue(restored.width > 0 && restored.height > 0)
    }

    @Test
    fun invalidCopyDoesNotReplaceSavedPhoto() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PhotoStore(context)
        val previous = "previous photo".toByteArray()
        store.photoFile.writeBytes(previous)
        val source = File(context.cacheDir, "invalid-photo")
        source.writeText("not an image")
        TestPhotoProvider.source = source
        val provider = Robolectric.buildContentProvider(TestPhotoProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("test.photos", provider)

        try {
            store.copyFrom(Uri.parse("content://test.photos/photo"))
            fail("Invalid photos must be rejected")
        } catch (_: IOException) {
            assertArrayEquals(previous, store.photoFile.readBytes())
        }
    }

    @Test
    fun sampleSizeBoundsLargeAndExtremeImages() {
        assertEquals(8, PhotoStore.sampleSize(8160, 6120, 1080, 1920))
        assertEquals(16, PhotoStore.sampleSize(1, 20000, 1080, 1920))
        assertEquals(1, PhotoStore.sampleSize(100, 200, 1080, 1920))
    }

    @Test
    fun decodesFiftyMegapixelSourceWithinScreenBounds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "large-photo.jpg")
        val image = BufferedImage(8160, 6120, BufferedImage.TYPE_BYTE_GRAY)
        assertTrue(ImageIO.write(image, "jpeg", source))
        image.flush()
        val decoded = PhotoStore(context).decode(source)
        val screen = context.resources.displayMetrics
        assertTrue(decoded.width <= screen.widthPixels)
        assertTrue(decoded.height <= screen.heightPixels)
    }
}

class TestPhotoProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        lateinit var source: File
    }
}
