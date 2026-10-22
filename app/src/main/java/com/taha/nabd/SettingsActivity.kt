package com.taha.nabd

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var prefs: InterestPrefs
    private lateinit var container: LinearLayout
    private var sectionCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = InterestPrefs(this)
        setTheme(prefs.themeStyle())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        container = findViewById(R.id.sections)
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val scroll = findViewById<ScrollView>(R.id.scroll)
        val y = scroll.scrollY
        container.removeAllViews()
        sectionCount = 0

        addHeader(R.string.section_reading)
        addChoice(R.string.theme_paper, prefs.themeId() == InterestPrefs.THEME_PAPER) {
            chooseTheme(InterestPrefs.THEME_PAPER)
        }
        addChoice(R.string.theme_charcoal, prefs.themeId() == InterestPrefs.THEME_CHARCOAL) {
            chooseTheme(InterestPrefs.THEME_CHARCOAL)
        }

        addHeader(R.string.section_text_size)
        addChoice(R.string.text_small, prefs.textSizeId() == ReadingText.SMALL) {
            chooseTextSize(ReadingText.SMALL)
        }
        addChoice(R.string.text_comfortable, prefs.textSizeId() == ReadingText.COMFORTABLE) {
            chooseTextSize(ReadingText.COMFORTABLE)
        }
        addChoice(R.string.text_large, prefs.textSizeId() == ReadingText.LARGE) {
            chooseTextSize(ReadingText.LARGE)
        }

        addHeader(R.string.section_browser)
        val inApp = prefs.useInAppBrowser()
        addRow(
            label = getString(R.string.browser_in_app),
            action = getString(if (inApp) R.string.browser_selected else R.string.browser_choose),
            highlight = inApp,
        ) {
            prefs.setInAppBrowser(true)
            render()
        }
        addRow(
            label = getString(R.string.browser_external),
            action = getString(if (inApp) R.string.browser_choose else R.string.browser_selected),
            highlight = !inApp,
        ) {
            prefs.setInAppBrowser(false)
            render()
        }

        addHeader(R.string.section_categories)
        NewsFeed.topics().forEach { topic ->
            val muted = prefs.isMutedTopic(topic.id)
            addRow(
                label = topic.label,
                action = getString(if (muted) R.string.show_again else R.string.hide),
                highlight = muted,
            ) {
                if (muted) prefs.unmuteTopic(topic.id) else prefs.muteTopic(topic.id)
                render()
            }
        }

        addHeader(R.string.section_sources)
        val sources = prefs.mutedSources().sortedBy { it.lowercase() }
        if (sources.isEmpty()) {
            addEmpty(R.string.no_muted_sources)
        } else {
            sources.forEach { source ->
                addRow(source, getString(R.string.show_again), highlight = true) {
                    prefs.unmuteSource(source)
                    render()
                }
            }
        }

        addHeader(R.string.section_stories)
        val stories = prefs.hiddenStories()
        if (stories.isEmpty()) {
            addEmpty(R.string.no_hidden_stories)
        } else {
            stories.forEach { story ->
                addRow(story.title, getString(R.string.show_again), highlight = true) {
                    prefs.unhideStory(story.url)
                    render()
                }
            }
        }

        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun addChoice(labelRes: Int, selected: Boolean, onPick: () -> Unit) {
        addRow(
            label = getString(labelRes),
            action = getString(if (selected) R.string.browser_selected else R.string.browser_choose),
            highlight = selected,
            onClick = onPick,
        )
    }

    private fun chooseTheme(id: String) {
        if (prefs.themeId() == id) return
        prefs.setThemeId(id)
        recreate()
    }

    private fun chooseTextSize(id: String) {
        if (prefs.textSizeId() == id) return
        prefs.setTextSizeId(id)
        render()
    }

    private fun addHeader(textRes: Int) {
        val header = TextView(this)
        header.setText(textRes)
        header.setTextColor(colorAttr(R.attr.nabdMuted))
        header.textSize = 13f
        val top = if (sectionCount++ == 0) dp(4) else dp(22)
        header.setPadding(0, top, 0, dp(6))
        container.addView(header)
    }

    private fun addEmpty(textRes: Int) {
        val empty = TextView(this)
        empty.setText(textRes)
        empty.setTextColor(colorAttr(R.attr.nabdMuted))
        empty.textSize = 15f
        empty.setPadding(0, dp(4), 0, dp(4))
        container.addView(empty)
    }

    private fun addRow(label: String, action: String, highlight: Boolean, onClick: () -> Unit) {
        val row = layoutInflater.inflate(R.layout.item_preference, container, false)
        row.findViewById<TextView>(R.id.label).text = label
        row.findViewById<Button>(R.id.action).apply {
            text = action
            setBackgroundColor(colorAttr(if (highlight) R.attr.nabdFilterSelected else R.attr.nabdFilterIdle))
            setTextColor(colorAttr(if (highlight) R.attr.nabdFilterSelectedText else R.attr.nabdText))
            backgroundTintList = null
            setOnClickListener { onClick() }
        }
        container.addView(row)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
