package com.saucedplussytv.androidtv.browse

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runs [action] after [delayMs] on the view-lifecycle scope; auto-cancels on view destruction. */
fun launchDelayed(owner: LifecycleOwner, delayMs: Long, action: Runnable): Job =
    owner.lifecycleScope.launch {
        delay(delayMs)
        action.run()
    }
