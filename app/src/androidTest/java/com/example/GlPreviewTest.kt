package com.example

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlPreviewTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun previewIsRecreatedAfterLeavingTheTab() {
        compose.onNodeWithTag("interactive_gl_preview").assertExists()
        compose.onNodeWithTag("nav_diagnostics").performClick()
        compose.onNodeWithTag("interactive_gl_preview").assertDoesNotExist()
        compose.onNodeWithTag("nav_preview").performClick()
        compose.onNodeWithTag("interactive_gl_preview").assertExists()
    }
}
