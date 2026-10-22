package com.taha.alkholasa

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class NewsAdapter : BaseAdapter() {
    var onMoreClick: ((anchor: View, article: Article) -> Unit)? = null
    var onOpenUrl: ((url: String) -> Unit)? = null
    var readingText: ReadingText = ReadingText.comfortable
    var mutedSources: Set<String> = emptySet()
    private val items = mutableListOf<Article>()
    private var expandedUrl: String? = null

    fun submit(articles: List<Article>) {
        items.clear()
        items.addAll(articles)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Article = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_article, parent, false)
        val article = items[position]
        val scale = readingText
        view.findViewById<TextView>(R.id.region).apply {
            text = article.regionLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, scale.metaSp)
        }
        view.findViewById<TextView>(R.id.topic).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, scale.metaSp)
            if (article.topics.isEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = "· " + article.topics.joinToString(" · ") { it.label }
            }
        }
        view.findViewById<TextView>(R.id.time).apply {
            val relative = relativeTime(article.publishedAt)
            text = if (relative.isEmpty()) "" else "· $relative"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, scale.metaSp)
        }
        view.findViewById<TextView>(R.id.title).apply {
            text = article.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, scale.titleSp)
            setLineSpacing(0f, 1.5f)
        }
        val related = visibleRelated(article)
        bindSource(view.findViewById(R.id.source), article, related, scale)
        bindRelated(view.findViewById(R.id.related), article, related, scale)
        view.findViewById<ImageView>(R.id.more).setOnClickListener { anchor ->
            onMoreClick?.invoke(anchor, article)
        }
        return view
    }

    private fun bindSource(
        sourceView: TextView,
        article: Article,
        related: List<Related>,
        scale: ReadingText,
    ) {
        sourceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scale.sourceSp)
        val label = if (related.isEmpty()) null else coverageLabel(sourceView.context, related.size)
        if (article.source.isBlank() && label == null) {
            sourceView.visibility = View.GONE
            sourceView.text = ""
            sourceView.movementMethod = null
            return
        }
        sourceView.visibility = View.VISIBLE
        val line = when {
            label == null -> article.source
            article.source.isBlank() -> label
            else -> "${article.source} · $label"
        }
        if (label == null) {
            sourceView.text = line
            sourceView.movementMethod = null
            return
        }
        val span = SpannableString(line)
        span.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    expandedUrl = if (expandedUrl == article.url) null else article.url
                    notifyDataSetChanged()
                }

                override fun updateDrawState(paint: TextPaint) {
                    paint.isUnderlineText = false
                    paint.color = sourceView.currentTextColor
                }
            },
            line.length - label.length,
            line.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        sourceView.text = span
        sourceView.movementMethod = LinkMovementMethod.getInstance()
        sourceView.highlightColor = Color.TRANSPARENT
    }

    private fun visibleRelated(article: Article): List<Related> =
        article.related.filter { item ->
            val source = item.source.trim()
            source.isEmpty() || mutedSources.none { it.equals(source, ignoreCase = true) }
        }

    private fun bindRelated(
        container: LinearLayout,
        article: Article,
        related: List<Related>,
        scale: ReadingText,
    ) {
        container.removeAllViews()
        if (expandedUrl != article.url || related.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val context = container.context
        val color = context.colorAttr(R.attr.nabdMuted)
        val density = context.resources.displayMetrics.density
        related.forEachIndexed { index, item ->
            val line = TextView(context).apply {
                text = if (item.source.isBlank()) item.title else "${item.source} · ${item.title}"
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, scale.metaSp)
                setLineSpacing(0f, 1.25f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = false
                setOnClickListener { onOpenUrl?.invoke(item.url) }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.topMargin = ((if (index == 0) 8 else 6) * density).toInt()
            container.addView(line, params)
        }
    }

    private fun coverageLabel(context: Context, count: Int): String = when (count) {
        1 -> context.getString(R.string.coverage_one)
        2 -> context.getString(R.string.coverage_two)
        in 3..10 -> context.getString(R.string.coverage_few, count)
        else -> context.getString(R.string.coverage_many, count)
    }

    private fun relativeTime(publishedAt: Long): String {
        if (publishedAt <= 0L) return ""
        val seconds = ((System.currentTimeMillis() - publishedAt) / 1000).coerceAtLeast(0)
        return when {
            seconds < 3600 -> "منذ ${seconds.coerceAtLeast(60) / 60} دقيقة"
            seconds < 86400 -> "منذ ${seconds / 3600} ساعة"
            else -> "منذ ${seconds / 86400} يوم"
        }
    }
}
