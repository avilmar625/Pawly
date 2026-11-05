package com.pawly.app.content

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Public Google News RSS search. No API key, account, or backend is required.
 * Articles always open at their original publisher; Pawly only displays RSS metadata.
 */
internal object DogNewsRepository {
    private const val BASE_URL = "https://news.google.com/rss/search?q="
    private val englishDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
    private val spanishDate = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "ES"))

    suspend fun load(section: PawlyContentSection): List<DogNewsArticle> = withContext(Dispatchers.IO) {
        val query = when (section) {
            PawlyContentSection.ADOPTION -> "perros adopción protectoras refugios España"
            PawlyContentSection.TRAINING -> "adiestramiento canino educación canina etología perros"
            else -> "perros mascotas bienestar animal actualidad España"
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("$BASE_URL$encoded&hl=es&gl=ES&ceid=ES:es")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "PawlyAndroid/1.0 (dog welfare reader)")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "El servicio de noticias ha respondido con HTTP ${connection.responseCode}."
            }
            connection.inputStream.use { input ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(input, "UTF-8")
                parseItems(parser, section)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseItems(parser: XmlPullParser, section: PawlyContentSection): List<DogNewsArticle> {
        val results = mutableListOf<DogNewsArticle>()
        var event = parser.eventType
        var insideItem = false
        var currentTag = ""
        var title = ""
        var link = ""
        var source = ""
        var description = ""
        var pubDate = ""

        while (event != XmlPullParser.END_DOCUMENT && results.size < 35) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.lowercase(Locale.ROOT)
                    if (currentTag == "item") {
                        insideItem = true
                        title = ""; link = ""; source = ""; description = ""; pubDate = ""
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (insideItem) {
                    val value = parser.text.orEmpty().trim()
                    when (currentTag) {
                        "title" -> if (title.isBlank()) title = value
                        "link" -> if (link.isBlank()) link = value
                        "source" -> if (source.isBlank()) source = value
                        "description" -> if (description.isBlank()) description = value
                        "pubdate" -> if (pubDate.isBlank()) pubDate = value
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true)) {
                        insideItem = false
                        if (title.isNotBlank() && link.startsWith("https://")) {
                            val millis = runCatching { englishDate.parse(pubDate)?.time ?: 0L }.getOrDefault(0L)
                            val cleanDescription = Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT)
                                .toString().replace(Regex("\\s+"), " ").trim().take(260)
                            val cleanTitle = Html.fromHtml(title, Html.FROM_HTML_MODE_COMPACT).toString().trim()
                            results += DogNewsArticle(
                                title = cleanTitle,
                                url = link,
                                source = source.ifBlank { "Fuente original" },
                                summary = cleanDescription,
                                publishedAt = if (millis > 0L) spanishDate.format(millis) else "Actualidad",
                                publishedAtMillis = millis,
                                section = section
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }
        return results.distinctBy { it.url }.sortedByDescending { it.publishedAtMillis }
    }
}
