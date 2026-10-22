package com.taha.nabd

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : Activity() {
    private val adapter = NewsAdapter()
    private lateinit var prefs: InterestPrefs
    private var articles: List<Article> = emptyList()
    private var region = "all"
    private var loadGeneration = 0
    private var loaded = false
    private var lastLoadFailed = false
    private var failedLabels: List<String> = emptyList()
    private lateinit var statusView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var retryView: Button
    private lateinit var swipeView: SwipeRefreshLayout
    private var appliedTheme = InterestPrefs.THEME_PAPER

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = InterestPrefs(this)
        appliedTheme = prefs.themeId()
        setTheme(prefs.themeStyle())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = findViewById<ListView>(R.id.newsList)
        statusView = findViewById(R.id.status)
        progressView = findViewById(R.id.progress)
        retryView = findViewById(R.id.retry)
        swipeView = findViewById(R.id.swipe)
        val filters = listOf(
            findViewById<Button>(R.id.filterAll) to "all",
            findViewById<Button>(R.id.filterSaudi) to "sa",
            findViewById<Button>(R.id.filterEgypt) to "eg",
        )

        adapter.onMoreClick = { anchor, article -> showArticleMenu(anchor, article) }
        adapter.onOpenUrl = { openArticle(it) }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            openArticle(adapter.getItem(position).url)
        }
        filters.forEach { (button, id) ->
            button.setOnClickListener {
                region = id
                styleFilters(filters)
                show()
            }
        }
        findViewById<Button>(R.id.refresh).setOnClickListener { load() }
        findViewById<Button>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        retryView.setOnClickListener { load() }
        swipeView.setColorSchemeColors(colorAttr(R.attr.nabdText))
        swipeView.setProgressBackgroundColorSchemeColor(colorAttr(R.attr.nabdFilterIdle))
        swipeView.setOnRefreshListener { load() }
        styleFilters(filters)
        load()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.themeId() != appliedTheme) {
            recreate()
            return
        }
        if (loaded) show()
    }

    private fun showArticleMenu(anchor: View, article: Article) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(getString(R.string.not_interested))
        popup.setOnMenuItemClickListener {
            askWhy(article)
            true
        }
        popup.show()
    }

    private fun askWhy(article: Article) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_not_interested, null)
        view.findViewById<TextView>(R.id.storyTitle).text = article.title

        val topicReasons = view.findViewById<LinearLayout>(R.id.topicReasons)
        if (article.topics.isEmpty()) {
            topicReasons.visibility = View.GONE
        } else {
            topicReasons.visibility = View.VISIBLE
            article.topics.forEach { topic ->
                val button = Button(this).apply {
                    text = getString(R.string.reason_category, topic.label)
                    setTextColor(colorAttr(R.attr.nabdText))
                    setBackgroundColor(colorAttr(R.attr.nabdFilterIdle))
                    backgroundTintList = null
                    isAllCaps = false
                    setOnClickListener {
                        prefs.muteTopic(topic.id)
                        finishChoice(dialog, getString(R.string.muted_category, topic.label))
                    }
                }
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                if (topicReasons.childCount > 0) {
                    params.topMargin = (8 * resources.displayMetrics.density).toInt()
                }
                topicReasons.addView(button, params)
            }
        }

        view.findViewById<Button>(R.id.reasonSource).apply {
            val source = article.source.trim()
            if (source.isEmpty()) {
                visibility = View.GONE
            } else {
                text = getString(R.string.reason_source, source)
                setOnClickListener {
                    prefs.muteSource(source)
                    finishChoice(dialog, getString(R.string.muted_source, source))
                }
            }
        }

        view.findViewById<Button>(R.id.reasonStory).apply {
            if (article.url.isBlank()) {
                visibility = View.GONE
            } else {
                setOnClickListener {
                    prefs.hideStory(article.url, article.title)
                    finishChoice(dialog, getString(R.string.muted_story))
                }
            }
        }

        view.findViewById<View>(R.id.reasonCancel).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorAttr(R.attr.nabdBackground)))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun finishChoice(dialog: Dialog, message: String) {
        dialog.dismiss()
        show()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun styleFilters(filters: List<Pair<Button, String>>) {
        filters.forEach { (button, id) ->
            val selected = id == region
            button.setBackgroundColor(
                colorAttr(if (selected) R.attr.nabdFilterSelected else R.attr.nabdFilterIdle),
            )
            button.setTextColor(
                colorAttr(if (selected) R.attr.nabdFilterSelectedText else R.attr.nabdText),
            )
            button.backgroundTintList = null
        }
    }

    private fun show() {
        val cutoff = System.currentTimeMillis() - DAY_MILLIS
        val visible = NewsFeed.combined(
            articles
                .filter { it.publishedAt >= cutoff }
                .filter { prefs.allows(it) }
                .filter { region == "all" || it.region == region },
        )
        adapter.readingText = prefs.readingText()
        adapter.mutedSources = prefs.mutedSources()
        adapter.submit(visible)
        when {
            !loaded && !lastLoadFailed -> {
                statusView.setText(R.string.loading)
                retryView.visibility = View.GONE
            }
            !loaded && lastLoadFailed -> {
                statusView.setText(R.string.load_failed)
                retryView.visibility = View.VISIBLE
            }
            lastLoadFailed -> {
                statusView.text = getString(R.string.article_count_stale, visible.size)
                retryView.visibility = View.VISIBLE
            }
            failedLabels.isNotEmpty() -> {
                statusView.text = getString(
                    R.string.article_count_partial,
                    visible.size,
                    failedLabels.joinToString(" و"),
                )
                retryView.visibility = View.VISIBLE
            }
            else -> {
                statusView.text = getString(R.string.article_count, visible.size)
                retryView.visibility = View.GONE
            }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        if (loaded) {
            swipeView.isRefreshing = true
        } else {
            progressView.visibility = View.VISIBLE
            statusView.setText(R.string.loading)
            retryView.visibility = View.GONE
        }
        Thread {
            val result = runCatching { NewsFeed.load() }
            runOnUiThread {
                if (generation != loadGeneration || isDestroyed) return@runOnUiThread
                progressView.visibility = View.GONE
                swipeView.isRefreshing = false
                result.fold(
                    onSuccess = { news ->
                        if (news.allFailed) {
                            lastLoadFailed = true
                        } else {
                            articles = news.articles
                            failedLabels = news.failedLabels
                            loaded = true
                            lastLoadFailed = false
                        }
                        show()
                    },
                    onFailure = {
                        lastLoadFailed = true
                        show()
                    },
                )
            }
        }.start()
    }

    private fun openArticle(url: String) {
        if (url.isBlank()) return
        val uri = Uri.parse(url)
        if (prefs.useInAppBrowser() && openInCustomTab(uri)) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun openInCustomTab(uri: Uri): Boolean {
        val surface = colorAttr(R.attr.nabdBackground)
        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(surface)
            .setNavigationBarColor(surface)
            .build()
        val scheme = if (prefs.themeId() == InterestPrefs.THEME_CHARCOAL) {
            CustomTabsIntent.COLOR_SCHEME_DARK
        } else {
            CustomTabsIntent.COLOR_SCHEME_LIGHT
        }
        val tabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(scheme)
            .setDefaultColorSchemeParams(colors)
            .build()
        return try {
            tabs.launchUrl(this, uri)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
