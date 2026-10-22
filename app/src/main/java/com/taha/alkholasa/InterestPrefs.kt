package com.taha.alkholasa

import android.content.Context

data class HiddenStory(val url: String, val title: String)

class InterestPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun themeId(): String = prefs.getString(KEY_THEME, THEME_PAPER) ?: THEME_PAPER

    fun setThemeId(id: String) {
        prefs.edit().putString(KEY_THEME, id).apply()
    }

    fun themeStyle(): Int =
        if (themeId() == THEME_CHARCOAL) R.style.Theme_Alkholasa_Charcoal else R.style.Theme_Alkholasa_Paper

    fun textSizeId(): String = prefs.getString(KEY_TEXT, ReadingText.COMFORTABLE) ?: ReadingText.COMFORTABLE

    fun setTextSizeId(id: String) {
        prefs.edit().putString(KEY_TEXT, id).apply()
    }

    fun readingText(): ReadingText = ReadingText.fromId(textSizeId())

    fun countries(): List<String> {
        val known = NewsFeed.countries()
        val saved = prefs.getStringSet(KEY_COUNTRIES, null)
        val picked = saved?.filter { id -> known.any { it.id == id } }?.toSet()
        val ids = if (saved == null || picked.isNullOrEmpty()) setOf("sa", "eg") else picked
        return known.map { it.id }.filter { it in ids }
    }

    fun setCountries(ids: Collection<String>): Boolean {
        val next = NewsFeed.countries().map { it.id }.filter { it in ids }
        if (next.isEmpty() || next.size > MAX_COUNTRIES) return false
        if (next == countries()) return false
        prefs.edit().putStringSet(KEY_COUNTRIES, next.toSet()).apply()
        return true
    }

    fun updateCountry(id: String, enabled: Boolean): CountryUpdate {
        if (NewsFeed.countries().none { it.id == id }) return CountryUpdate.SAVED
        val current = countries().toMutableList()
        if (enabled) {
            if (id in current) return CountryUpdate.SAVED
            if (current.size >= MAX_COUNTRIES) return CountryUpdate.TOO_MANY
            current += id
        } else {
            if (id !in current) return CountryUpdate.SAVED
            if (current.size <= 1) return CountryUpdate.NEED_ONE
            current -= id
        }
        prefs.edit().putStringSet(KEY_COUNTRIES, current.toSet()).apply()
        return CountryUpdate.SAVED
    }

    fun useInAppBrowser(): Boolean = prefs.getBoolean(KEY_IN_APP, true)

    fun setInAppBrowser(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IN_APP, enabled).apply()
    }

    fun isMutedTopic(topicId: String): Boolean = topicId in mutedTopicIds()

    fun mutedTopicIds(): Set<String> = prefs.getStringSet(KEY_TOPICS, emptySet()).orEmpty()

    fun mutedSources(): Set<String> = prefs.getStringSet(KEY_SOURCES, emptySet()).orEmpty()

    fun hiddenStories(): List<HiddenStory> =
        prefs.getStringSet(KEY_STORIES, emptySet()).orEmpty().mapNotNull { decodeStory(it) }
            .sortedBy { it.title }

    fun allows(article: Article): Boolean {
        if (article.topics.any { isMutedTopic(it.id) }) return false
        val source = article.source.trim()
        if (source.isNotEmpty() && mutedSources().any { it.equals(source, ignoreCase = true) }) {
            return false
        }
        return article.url.isBlank() || article.url !in hiddenUrls()
    }

    fun muteTopic(topicId: String) = add(KEY_TOPICS, topicId)

    fun unmuteTopic(topicId: String) = remove(KEY_TOPICS, topicId)

    fun muteSource(source: String) {
        val clean = source.trim()
        if (clean.isEmpty()) return
        val next = mutedSources().filterNot { it.equals(clean, ignoreCase = true) } + clean
        prefs.edit().putStringSet(KEY_SOURCES, next.toSet()).apply()
    }

    fun unmuteSource(source: String) = remove(KEY_SOURCES, source)

    fun hideStory(url: String, title: String) {
        if (url.isBlank()) return
        val titleClean = title.replace(SEP, " ").replace("\n", " ").trim()
        val next = hiddenStories()
            .filterNot { it.url == url }
            .map { encodeStory(it.url, it.title) }
            .toMutableSet()
        next += encodeStory(url, titleClean.ifBlank { url })
        prefs.edit().putStringSet(KEY_STORIES, next.toSet()).apply()
    }

    fun unhideStory(url: String) {
        val next = hiddenStories()
            .filterNot { it.url == url }
            .map { encodeStory(it.url, it.title) }
            .toSet()
        prefs.edit().putStringSet(KEY_STORIES, next).apply()
    }

    private fun hiddenUrls(): Set<String> =
        prefs.getStringSet(KEY_STORIES, emptySet()).orEmpty().mapNotNull { raw ->
            val sep = raw.indexOf(SEP)
            if (sep <= 0) null else raw.substring(0, sep)
        }.toSet()

    private fun add(key: String, value: String) {
        val next = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        next += value
        prefs.edit().putStringSet(key, next.toSet()).apply()
    }

    private fun remove(key: String, value: String) {
        val next = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        next -= value
        prefs.edit().putStringSet(key, next.toSet()).apply()
    }

    private fun encodeStory(url: String, title: String) = url + SEP + title

    private fun decodeStory(raw: String): HiddenStory? {
        val sep = raw.indexOf(SEP)
        if (sep <= 0 || sep >= raw.lastIndex) return null
        return HiddenStory(raw.substring(0, sep), raw.substring(sep + 1))
    }

    enum class CountryUpdate { SAVED, NEED_ONE, TOO_MANY }

    companion object {
        const val THEME_PAPER = "paper"
        const val THEME_CHARCOAL = "charcoal"
        const val MAX_COUNTRIES = 6

        private const val FILE = "interests"
        private const val KEY_COUNTRIES = "countries"
        private const val KEY_THEME = "reading_theme"
        private const val KEY_TEXT = "reading_text"
        private const val KEY_IN_APP = "in_app_browser"
        private const val KEY_TOPICS = "muted_topics"
        private const val KEY_SOURCES = "muted_sources"
        private const val KEY_STORIES = "hidden_stories"
        private const val SEP = "\u001f"
    }
}
