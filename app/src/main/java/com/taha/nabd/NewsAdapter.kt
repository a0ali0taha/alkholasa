package com.taha.nabd

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class NewsAdapter : BaseAdapter() {
    var onMoreClick: ((anchor: View, article: Article) -> Unit)? = null
    var readingText: ReadingText = ReadingText.comfortable
    private val items = mutableListOf<Article>()

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
        view.findViewById<TextView>(R.id.source).apply {
            text = article.source
            setTextSize(TypedValue.COMPLEX_UNIT_SP, scale.sourceSp)
        }
        view.findViewById<ImageView>(R.id.more).setOnClickListener { anchor ->
            onMoreClick?.invoke(anchor, article)
        }
        return view
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
