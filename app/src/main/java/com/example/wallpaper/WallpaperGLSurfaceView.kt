package com.example.wallpaper

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.SurfaceHolder

/**
 * GLSurfaceView customized to bind to a WallpaperService.Engine's SurfaceHolder.
 */
class WallpaperGLSurfaceView(
    context: Context,
    private val engineSurfaceHolder: SurfaceHolder
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
    }

    override fun getHolder(): SurfaceHolder {
        return engineSurfaceHolder
    }

    fun onDestroy() {
        super.onDetachedFromWindow()
    }
}
