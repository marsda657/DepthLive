package com.example.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WallpaperRepository
import com.example.llm.MotionPromptManager
import com.example.llm.ApiKeyStore
import com.example.llm.ChatMotionSelector
import com.example.llm.LlmProvider
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.model.MotionPreset
import com.example.model.PresetsData
import com.example.ui.components.GlLivePreview
import com.example.wallpaper.DepthLiveWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Preview Screen with interactive OpenGL live canvas, one-tap photo picker,
 * motion prompt bar, and 12-preset carousel.
 */
@Composable
fun PreviewTab(
    paddingValues: PaddingValues,
    onNavigateToTuning: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scene by WallpaperRepository.activeScene.collectAsState()
    val config by WallpaperRepository.shaderConfig.collectAsState()
    val isProcessing by WallpaperRepository.isProcessing.collectAsState()

    var promptInput by remember { mutableStateOf("") }
    var promptStatus by remember { mutableStateOf<String?>(null) }
    var isGeneratingPrompt by remember { mutableStateOf(false) }
    var useAi by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(LlmProvider.MISTRAL) }
    val keyStore = remember { ApiKeyStore(context) }
    var keyInput by remember { mutableStateOf("") }
    var keySaved by remember(provider) { mutableStateOf(keyStore.hasKey(provider)) }

    fun applyPrompt(text: String) {
        if (isGeneratingPrompt || text.isBlank()) return
        isGeneratingPrompt = true
        val requestedProvider = provider
        val baseConfig = config
        val selector: (suspend (String) -> String)? =
            if (useAi) { value ->
                val key = keyStore.get(requestedProvider) ?: throw IllegalStateException("No API key saved")
                ChatMotionSelector.select(requestedProvider, key, value)
            } else null
        coroutineScope.launch {
            try {
                val result = MotionPromptManager.parseMotionPrompt(text, baseConfig, selector)
                if (WallpaperRepository.shaderConfig.value == baseConfig) {
                    WallpaperRepository.updateConfig(result.config)
                    promptStatus = result.summary
                } else {
                    promptStatus = "Settings changed while generating; apply the prompt again."
                }
            } finally {
                isGeneratingPrompt = false
            }
        }
    }

    // Zero-permission modern Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                    requireNotNull(bitmap) { "Could not decode the selected photo" }
                    WallpaperRepository.setCustomPhoto(context, bitmap, "User Photo")
                    Toast.makeText(context, "Photo analyzed", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val sampleSuggestions = listOf(
        "Light snow, slow zoom",
        "Stormy ocean rain",
        "Sunset dream, calm breeze",
        "Cyber neon, true black",
        "Audio pulse with fireflies"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Interactive GL Preview Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14f)
                .clip(RoundedCornerShape(20.dp))
        ) {
            GlLivePreview(
                scene = scene,
                config = config,
                modifier = Modifier.fillMaxSize()
            )

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Text(
                            text = "Analyzing image depth & segmenting layers...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. One-Tap Primary Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Set Wallpaper Button
            Button(
                onClick = {
                    setAsLiveWallpaper(context)
                },
                modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp)
                    .testTag("set_live_wallpaper_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color(0xFF032840)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Wallpaper,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Set Live Wallpaper",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Pick Photo Button
            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("pick_photo_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFFF1F5F9)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pick Photo",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }

        // Sample photo quick reload
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scene: ${scene?.name ?: "Loading..."}",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        WallpaperRepository.loadBundledSampleScene(context)
                        Toast.makeText(context, "Loaded Alpine Lake 5-layer scene", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp).testTag("load_sample_scene_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Landscape,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Alpine Lake Sample",
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        // 3. Motion Prompt Field (Natural Language -> Shader Parameters)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Motion Prompt",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Text(
                    text = "Offline by default. If enabled, only the prompt text is sent to the chosen provider with your own API key; photos stay on-device.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use ${provider.label} for this prompt", color = Color.White, fontSize = 12.sp)
                    Switch(checked = useAi, onCheckedChange = { useAi = it }, enabled = !isGeneratingPrompt)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LlmProvider.values().forEach { p ->
                        if (p == provider) Button(onClick = {}, enabled = !isGeneratingPrompt) { Text(p.label, fontSize = 12.sp) }
                        else OutlinedButton(onClick = {
                            provider = p
                            keyInput = ""
                        }, enabled = !isGeneratingPrompt) { Text(p.label, fontSize = 12.sp) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text(if (keySaved) "Key saved (encrypted)" else "${provider.label} API key", fontSize = 12.sp) }
                    )
                    OutlinedButton(onClick = {
                        keyStore.put(provider, keyInput)
                        keyInput = ""
                        keySaved = keyStore.hasKey(provider)
                    }, enabled = keyInput.isNotBlank() && !isGeneratingPrompt) { Text("Save", fontSize = 12.sp) }
                    if (keySaved) {
                        OutlinedButton(onClick = {
                            keyStore.remove(provider)
                            keyInput = ""
                            keySaved = false
                        }, enabled = !isGeneratingPrompt) { Text("Delete", fontSize = 12.sp) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it.take(500) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("motion_prompt_input"),
                        placeholder = {
                            Text("e.g. 'light snow, slow zoom'", fontSize = 12.sp, color = Color(0xFF64748B))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0B0F17),
                            unfocusedContainerColor = Color(0xFF0B0F17),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Button(
                        onClick = { applyPrompt(promptInput) },
                        enabled = !isGeneratingPrompt && promptInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("apply_motion_prompt_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA855F7),
                            contentColor = Color.White
                        )
                    ) {
                        if (isGeneratingPrompt) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Suggestion chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sampleSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = {
                                promptInput = suggestion
                                applyPrompt(suggestion)
                            },
                            enabled = !isGeneratingPrompt,
                            label = { Text(suggestion, fontSize = 11.sp, color = Color(0xFFCBD5E1)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF1E293B)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = !isGeneratingPrompt,
                                borderColor = Color(0xFF334155)
                            )
                        )
                    }
                }

                if (promptStatus != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✓ ${promptStatus!!}",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. 12 Built-in Motion Presets Carousel
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "12 Motion Presets (Offline Fallback)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "12 Available",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PresetsData.presets.forEach { preset ->
                    PresetCard(
                        preset = preset,
                        isSelected = false,
                        onSelect = {
                            WallpaperRepository.updateConfig(preset.config.copy(audioReactive = config.audioReactive))
                            Toast.makeText(context, "Applied: ${preset.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PresetCard(
    preset: MotionPreset,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(170.dp)
            .clickable { onSelect() }
            .testTag("preset_card_${preset.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF1E293B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = preset.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )

            Text(
                text = preset.description,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 3,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Launches Android Live Wallpaper picker or settings directly.
 */
fun setAsLiveWallpaper(context: Context) {
    try {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, DepthLiveWallpaperService::class.java)
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general live wallpaper chooser
        try {
            val fallback = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
            context.startActivity(fallback)
        } catch (e2: Exception) {
            Toast.makeText(context, "Please set DepthLive from your phone's Wallpaper Settings", Toast.LENGTH_LONG).show()
        }
    }
}
