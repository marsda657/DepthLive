package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.WallpaperRepository
import com.example.ui.MainScreen
import com.example.ui.theme.DepthLiveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Wallpaper repository & load bundled landscape scene
        WallpaperRepository.init(applicationContext)

        setContent {
            DepthLiveTheme {
                MainScreen()
            }
        }
    }
}
