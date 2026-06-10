package com.saucedplussytv.androidtv.browse

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient
import com.saucedplussytv.androidtv.models.Video
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.NavigableMap
import java.util.function.Consumer
import kotlin.coroutines.resume

/** Runs [action] after [delayMs] on the view-lifecycle scope; auto-cancels on view destruction. */
fun launchDelayed(owner: LifecycleOwner, delayMs: Long, action: Runnable): Job =
    owner.lifecycleScope.launch {
        delay(delayMs)
        action.run()
    }

/**
 * Polls each live stream in [strms] every 10 s until one returns HTTP 200, then calls [onLiveFound].
 * Bound to [owner]'s lifecycle; also returns a [Job] the caller can cancel on logout.
 */
fun startLiveCheckLoop(
    owner: LifecycleOwner,
    strms: NavigableMap<Int, Video>,
    client: SaucedplussyTVClient,
    onLiveFound: Consumer<Video>
): Job = owner.lifecycleScope.launch {
    var idx = strms.firstKey()
    while (isActive) {
        val stream = strms[idx] ?: break
        val url = stream.vidUrl ?: ""
        if (url.isNotEmpty()) {
            val status = suspendCancellableCoroutine { cont ->
                client.checkLive(url) { s ->
                    if (cont.isActive) cont.resume(s)
                    Unit
                }
            }
            if (status == 200) {
                onLiveFound.accept(stream)
                return@launch
            }
        }
        val next = strms.higherKey(idx)
        idx = next ?: strms.firstKey()
        delay(10_000)
    }
}
