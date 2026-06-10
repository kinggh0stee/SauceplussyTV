package com.saucedplussytv.androidtv.util

/** Wraps a LiveData value that should only be consumed once (navigation, dialogs, toasts). */
class Event<out T>(private val content: T) {
    private var hasBeenHandled = false

    fun getContentIfNotHandled(): T? = if (hasBeenHandled) null else {
        hasBeenHandled = true
        content
    }
}
