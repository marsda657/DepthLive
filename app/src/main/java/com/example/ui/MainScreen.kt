package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AmbientWeatherTab
import com.example.ui.screens.DiagnosticsTab
import com.example.ui.screens.LayerTuningTab
import com.example.ui.screens.PreviewTab
import com.example.ui.screens.setAsLiveWallpaper

enum class AppTab(val label: String, val icon: ImageVector, val tag: String) {
    PREVIEW("Preview", Icons.Default.Wallpaper, "nav_preview"),
    TUNING("Shaders", Icons.Default.Tune, "nav_tuning"),
    DIAGNOSTICS("Diagnostics", Icons.Default.Analytics, "nav_diagnostics"),
    AMBIENT("Ambient", Icons.Default.WbSunny, "nav_ambient")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.PREVIEW) }

    // Intercept back button to return to Preview tab first
    BackHandler(enabled = currentTab != AppTab.PREVIEW) {
        currentTab = AppTab.PREVIEW
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("app_header_title")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF38BDF8), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF032840),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "DepthLive",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v1.0 • 60 FPS",
                                    fontSize = 10.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    actions = {
                        Button(
                            onClick = { setAsLiveWallpaper(context) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF38BDF8),
                                contentColor = Color(0xFF032840)
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("top_set_wallpaper_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Set", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0B0F17)
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = Color(0xFF0F172A),
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        AppTab.values().forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF032840),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    indicatorColor = Color(0xFF38BDF8),
                                    unselectedIconColor = Color(0xFF94A3B8),
                                    unselectedTextColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFF0B0F17)
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxHeight().testTag("navigation_rail")
                    ) {
                        AppTab.values().forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color(0xFF032840),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    indicatorColor = Color(0xFF38BDF8),
                                    unselectedIconColor = Color(0xFF94A3B8),
                                    unselectedTextColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        AppTab.PREVIEW -> PreviewTab(
                            paddingValues = innerPadding,
                            onNavigateToTuning = { currentTab = AppTab.TUNING }
                        )
                        AppTab.TUNING -> LayerTuningTab(paddingValues = innerPadding)
                        AppTab.DIAGNOSTICS -> DiagnosticsTab(paddingValues = innerPadding)
                        AppTab.AMBIENT -> AmbientWeatherTab(paddingValues = innerPadding)
                    }
                }
            }
        }
    }
}
