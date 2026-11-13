package com.example.llm

enum class LlmProvider(
    val label: String,
    val endpoint: String,
    val model: String
) {
    MISTRAL("Mistral AI", "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest"),
    GROK("Grok (xAI)", "https://api.x.ai/v1/chat/completions", "grok-3-mini")
}
