package com.taha.alkholasa

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.colorAttr(@AttrRes attr: Int): Int {
    val values = obtainStyledAttributes(intArrayOf(attr))
    return try {
        values.getColor(0, 0)
    } finally {
        values.recycle()
    }
}
