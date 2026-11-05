package com.pawly.app.content

internal enum class PawlyContentSection(val label: String) {
    NEWS("Noticias"),
    ADOPTION("Adopción"),
    TRAINING("Educación"),
    VIDEOS("Vídeos"),
    GUIDES("Guías")
}

internal data class DogNewsArticle(
    val title: String,
    val url: String,
    val source: String,
    val summary: String,
    val publishedAt: String,
    val publishedAtMillis: Long,
    val section: PawlyContentSection
) {
    val id: String get() = url.hashCode().toString()
}

internal data class LearningGuide(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val points: List<String>,
    val note: String
)

internal data class ContentLink(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val url: String,
    val tag: String
)

internal data class VideoCollection(
    val title: String,
    val description: String,
    val searchQuery: String,
    val emoji: String
)
