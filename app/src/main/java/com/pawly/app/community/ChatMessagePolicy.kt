package com.pawly.app.community

internal data class ChatMessagePayload(
    val text: String,
    val imageUrl: String,
    val videoUrl: String
)

/** Pure validation shared by the chat UI/repository before any Firestore write. */
internal object ChatMessagePolicy {
    fun prepare(text: String, imageUrl: String = "", videoUrl: String = ""): ChatMessagePayload {
        val cleanText = text.trim()
        val cleanImageUrl = imageUrl.trim()
        val cleanVideoUrl = videoUrl.trim()

        require(cleanText.length <= 2000) { "El mensaje no puede superar los 2000 caracteres." }
        require(cleanText.isNotBlank() || cleanImageUrl.isNotBlank() || cleanVideoUrl.isNotBlank()) {
            "Escribe un mensaje o adjunta una fotografía o un vídeo."
        }
        require(cleanImageUrl.length <= 2048 && cleanVideoUrl.length <= 2048) {
            "La dirección del archivo multimedia no es válida."
        }
        require(cleanImageUrl.isBlank() || cleanVideoUrl.isBlank()) {
            "Adjunta una fotografía o un vídeo, no ambos."
        }
        require(cleanImageUrl.isBlank() || cleanImageUrl.startsWith("https://", ignoreCase = true)) {
            "La dirección de la fotografía debe ser HTTPS."
        }
        require(cleanVideoUrl.isBlank() || cleanVideoUrl.startsWith("https://", ignoreCase = true)) {
            "La dirección del vídeo debe ser HTTPS."
        }
        return ChatMessagePayload(cleanText, cleanImageUrl, cleanVideoUrl)
    }
}
