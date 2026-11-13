package com.example.llm

import android.content.Context
import com.example.model.PresetsData
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

object GeminiMotionSelector {
    suspend fun select(context: Context, prompt: String): String {
        check(FirebaseApp.getApps(context).isNotEmpty()) {
            "Firebase is not configured for this application"
        }
        val options = PresetsData.presets.joinToString("\n") { "${it.id}: ${it.description}" }
        val instruction = """
            Select the closest motion preset for the user's description.
            Return only one preset identifier from the list, with no explanation.
            Presets:
            $options

            User description:
            $prompt
        """.trimIndent()
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-2.5-flash-lite")
        return model.generateContent(instruction).text
            ?: throw IllegalStateException("Gemini returned no text")
    }
}
