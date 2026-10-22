package com.taha.nabd

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Topic(val id: String, val label: String, val code: String)

data class NewsLoad(
    val articles: List<Article>,
    val failedLabels: List<String>,
    val allFailed: Boolean,
)

data class Related(
    val title: String,
    val source: String,
    val url: String,
)

data class Article(
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: Long,
    val region: String,
    val regionLabel: String,
    val topics: List<Topic> = emptyList(),
    val related: List<Related> = emptyList(),
)

private data class Edition(val id: String, val label: String, val gl: String)

private data class Feed(val edition: Edition, val topic: Topic?, val url: String)

private val RELATED_PATTERN = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
private const val MAX_RELATED = 8

object NewsFeed {
    private val topicCatalog = listOf(
        Topic("sports", "رياضة", "SPORTS"),
        Topic("entertainment", "ترفيه", "ENTERTAINMENT"),
        Topic("world", "أخبار دولية", "WORLD"),
        Topic("business", "أعمال", "BUSINESS"),
        Topic("technology", "تكنولوجيا", "TECHNOLOGY"),
        Topic("science", "علوم", "SCIENCE"),
        Topic("health", "صحة", "HEALTH"),
    )

    private val editions = listOf(
        Edition("sa", "السعودية", "SA"),
        Edition("eg", "مصر", "EG"),
    )

    private val feeds: List<Feed> = editions.flatMap { edition ->
        val general = "https://news.google.com/rss?hl=ar&gl=${edition.gl}&ceid=${edition.gl}:ar"
        listOf(Feed(edition, null, general)) + topicCatalog.map { topic ->
            Feed(
                edition,
                topic,
                "https://news.google.com/rss/headlines/section/topic/${topic.code}?hl=ar&gl=${edition.gl}&ceid=${edition.gl}:ar",
            )
        }
    }

    fun topics(): List<Topic> = topicCatalog

    private val rssDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    fun load(): NewsLoad {
        val pool = Executors.newFixedThreadPool(feeds.size)
        try {
            val tasks = feeds.map { feed -> feed to pool.submit<List<Article>> { fetch(feed) } }
            val fetched = mutableListOf<Article>()
            val failedEditionIds = mutableSetOf<String>()
            for ((feed, task) in tasks) {
                runCatching { task.get(25, TimeUnit.SECONDS) }
                    .onSuccess { fetched += it }
                    .onFailure { if (feed.topic == null) failedEditionIds += feed.edition.id }
            }
            val failedLabels = editions.filter { it.id in failedEditionIds }.map { it.label }
            return NewsLoad(
                articles = merge(fetched),
                failedLabels = failedLabels,
                allFailed = failedEditionIds.size == editions.size,
            )
        } finally {
            pool.shutdownNow()
        }
    }

    private fun merge(items: List<Article>): List<Article> {
        val found = mutableMapOf<String, MutableSet<Topic>>()
        items.filter { it.topics.isNotEmpty() }.forEach { article ->
            found.getOrPut(article.region + "\n" + normalize(article.title)) { linkedSetOf() }
                .addAll(article.topics)
        }
        return items
            .filter { it.topics.isEmpty() }
            .map { article ->
                val topics = found[article.region + "\n" + normalize(article.title)].orEmpty()
                article.copy(topics = topicCatalog.filter { it in topics })
            }
            .sortedByDescending { it.publishedAt }
    }

    fun combined(articles: List<Article>): List<Article> {
        val grouped = linkedMapOf<String, MutableList<Article>>()
        for (article in articles) {
            grouped.getOrPut(normalize(article.title)) { mutableListOf() }.add(article)
        }
        return grouped.values
            .map { group -> if (group.size == 1) group.first() else foldEditions(group) }
            .sortedByDescending { it.publishedAt }
    }

    private fun foldEditions(group: List<Article>): Article {
        val ordered = group.sortedByDescending { it.publishedAt }
        val newest = ordered.first()
        val labels = editions.mapNotNull { edition ->
            if (group.any { it.region == edition.id }) edition.label else null
        }
        val topics = topicCatalog.filter { topic -> group.any { topic in it.topics } }
        val seen = mutableSetOf(normalize(newest.title))
        val related = mutableListOf<Related>()
        for (item in ordered.flatMap { it.related }) {
            val key = normalize(item.title)
            if (key.isEmpty() || key in seen || related.any { it.url == item.url }) continue
            seen += key
            related += item
            if (related.size == MAX_RELATED) break
        }
        return newest.copy(
            regionLabel = labels.joinToString(" · ").ifBlank { newest.regionLabel },
            topics = topics,
            related = related,
        )
    }

    private fun normalize(title: String) = title.trim().replace(Regex("\\s+"), " ")

    private val relatedItem = Regex("""<li\b[^>]*>(.*?)</li>""", RELATED_PATTERN)
    private val relatedLink = Regex("""<a\s+[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RELATED_PATTERN)
    private val relatedSource = Regex("""<font\b[^>]*>(.*?)</font>""", RELATED_PATTERN)

    private fun fetch(feed: Feed): List<Article> {
        val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            connection.inputStream.use { return parse(Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(it, "UTF-8")
            }, feed) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(parser: XmlPullParser, feed: Feed): List<Article> {
        val articles = mutableListOf<Article>()
        var inItem = false
        var title = ""
        var link = ""
        var source = ""
        var description = ""
        var publishedAt = 0L
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "item") {
                    inItem = true
                    title = ""
                    link = ""
                    source = ""
                    description = ""
                    publishedAt = 0L
                } else if (inItem) {
                    when (parser.name) {
                        "title" -> title = parser.nextText().trim()
                        "link" -> link = parser.nextText().trim()
                        "source" -> source = parser.nextText().trim()
                        "pubDate" -> publishedAt = parseDate(parser.nextText().trim())
                        "description" -> description = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    articles += toArticle(title, source, link, publishedAt, description, feed)
                    inItem = false
                }
            }
            event = parser.next()
        }
        return articles
    }

    private fun toArticle(
        rawTitle: String,
        rawSource: String,
        link: String,
        publishedAt: Long,
        description: String,
        feed: Feed,
    ): Article {
        val splitAt = rawTitle.lastIndexOf(" - ")
        val title = if (splitAt > 0) rawTitle.substring(0, splitAt).trim() else rawTitle
        val source = rawSource.ifBlank {
            if (splitAt > 0) rawTitle.substring(splitAt + 3).trim() else ""
        }
        val headline = title.ifBlank { rawTitle }
        return Article(
            title = headline,
            source = source,
            url = link,
            publishedAt = publishedAt,
            region = feed.edition.id,
            regionLabel = feed.edition.label,
            topics = listOfNotNull(feed.topic),
            related = if (feed.topic == null) parseRelated(description, headline, link) else emptyList(),
        )
    }

    private fun parseRelated(html: String, mainTitle: String, mainUrl: String): List<Related> {
        if (html.isBlank()) return emptyList()
        val main = normalize(mainTitle)
        val found = mutableListOf<Related>()
        for (block in relatedItem.findAll(html)) {
            val body = block.groupValues[1]
            val link = relatedLink.find(body) ?: continue
            val url = cleanText(link.groupValues[1])
            val title = cleanText(link.groupValues[2])
            val source = relatedSource.find(body)?.let { cleanText(it.groupValues[1]) }.orEmpty()
            if (url.isBlank() || title.isBlank()) continue
            if (url == mainUrl || normalize(title) == main) continue
            found += Related(title, source, url)
        }
        return found.distinctBy { it.url }.take(MAX_RELATED)
    }

    private fun cleanText(value: String) =
        value.replace(Regex("<[^>]+>"), " ")
            .replace('\u00a0', ' ')
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun parseDate(value: String): Long {
        return try {
            synchronized(rssDate) { rssDate.parse(value)?.time ?: 0L }
        } catch (_: Exception) {
            0L
        }
    }
}
