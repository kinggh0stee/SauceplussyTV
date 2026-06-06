package com.saucedplussytv.androidtv.ext

import android.content.Context
import android.graphics.Color
import com.saucedplussytv.androidtv.R

fun Context.getTagColor(name: String): Int {
    val colors = resources.getStringArray(R.array.default_colors)
    val c = name.lowercase()[0]
    val idx = if (c in 'a'..'z') c - 'a' else colors.size - 1
    return Color.parseColor(colors[idx])
}
