package com.taha.alkholasa

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object CountryPicker {
    fun show(activity: Activity, prefs: InterestPrefs, onChanged: () -> Unit) {
        val countries = NewsFeed.countries()
        val selected = prefs.countries().toMutableSet()
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val ink = activity.colorAttr(R.attr.nabdText)
        val muted = activity.colorAttr(R.attr.nabdMuted)
        val background = activity.colorAttr(R.attr.nabdBackground)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(background)
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }
        root.addView(TextView(activity).apply {
            setText(R.string.countries_dialog_title)
            setTextColor(ink)
            textSize = 18f
        })
        root.addView(TextView(activity).apply {
            setText(R.string.countries_hint)
            setTextColor(muted)
            textSize = 13f
            setPadding(0, dp(6), 0, dp(8))
        })

        val choices = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        countries.forEach { country ->
            val box = CheckBox(activity).apply {
                text = country.label
                isAllCaps = false
                setTextColor(ink)
                textSize = 16f
                buttonTintList = ColorStateList.valueOf(ink)
                isChecked = country.id in selected
                var guard = false
                setOnCheckedChangeListener { _, isChecked ->
                    if (guard) return@setOnCheckedChangeListener
                    if (isChecked) {
                        if (selected.size >= InterestPrefs.MAX_COUNTRIES) {
                            guard = true
                            this.isChecked = false
                            guard = false
                            Toast.makeText(activity, R.string.countries_too_many, Toast.LENGTH_SHORT).show()
                            return@setOnCheckedChangeListener
                        }
                        selected += country.id
                    } else if (selected.size <= 1) {
                        guard = true
                        this.isChecked = true
                        guard = false
                        Toast.makeText(activity, R.string.countries_need_one, Toast.LENGTH_SHORT).show()
                    } else {
                        selected -= country.id
                    }
                }
            }
            choices.addView(box)
        }
        root.addView(
            ScrollView(activity).apply { addView(choices) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        actions.addView(Button(activity).apply {
            setText(R.string.cancel)
            isAllCaps = false
            setTextColor(ink)
            setBackgroundColor(activity.colorAttr(R.attr.nabdFilterIdle))
            backgroundTintList = null
            setOnClickListener { dialog.dismiss() }
        })
        actions.addView(Button(activity).apply {
            setText(R.string.done)
            isAllCaps = false
            setTextColor(activity.colorAttr(R.attr.nabdFilterSelectedText))
            setBackgroundColor(activity.colorAttr(R.attr.nabdFilterSelected))
            backgroundTintList = null
            val gap = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gap.marginStart = dp(8)
            layoutParams = gap
            setOnClickListener {
                if (prefs.setCountries(selected)) onChanged()
                dialog.dismiss()
            }
        })
        root.addView(actions)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(background))
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.78f).toInt(),
        )
    }
}
