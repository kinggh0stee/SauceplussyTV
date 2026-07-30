package com.saucedplussytv.androidtv.ext

import android.content.Context
import androidx.core.graphics.toColorInt
import com.saucedplussytv.androidtv.R

fun Context.getTagColor(name: String): Int {
    val colors = resources.getStringArray(R.array.default_colors)
    // The API can return an empty tag string; indexing [0] on it would throw during card bind.
    val c = name.lowercase().firstOrNull()
    val idx = if (c != null && c in 'a'..'z') c - 'a' else colors.size - 1
    return colors[idx].toColorInt()
}
