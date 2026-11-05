package com.pawly.app

import android.content.SharedPreferences
import org.json.JSONArray
import com.pawly.app.content.DogNewsArticle
import com.pawly.app.content.PawlyContentSection

internal fun readSavedArticles(preferences: SharedPreferences): Map<String, DogNewsArticle> {
    return runCatching {
        val json = JSONArray(preferences.getString("saved_articles_json", "[]") ?: "[]")
        buildMap {
            for (index in 0 until json.length()) {
                val row = json.getJSONObject(index)
                val section = runCatching {
                    PawlyContentSection.valueOf(row.optString("section", PawlyContentSection.NEWS.name))
                }.getOrDefault(PawlyContentSection.NEWS)
                val article = DogNewsArticle(
                    title = row.optString("title"),
                    url = row.optString("url"),
                    source = row.optString("source"),
                    summary = row.optString("summary"),
                    publishedAt = row.optString("publishedAt"),
                    publishedAtMillis = row.optLong("publishedAtMillis"),
                    section = section
                )
                if (article.url.startsWith("https://")) put(article.id, article)
            }
        }
    }.getOrDefault(emptyMap())
}
