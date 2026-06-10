package com.saucedplussytv.androidtv.browse

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.saucedplussytv.androidtv.authenticate.AuthManager
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient
import com.saucedplussytv.androidtv.client.SocketClient
import com.saucedplussytv.androidtv.models.Delivery
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoProgress
import com.saucedplussytv.androidtv.subscription.Subscription
import com.saucedplussytv.androidtv.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.ArrayList
import java.util.HashMap
import java.util.NavigableMap
import java.util.TreeMap
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val client: SaucedplussyTVClient,
    val socketClient: SocketClient,
    val authManager: AuthManager
) : ViewModel() {

    // --- Data model fields (moved from MainFragment) ---
    private val _subscriptions: MutableList<Subscription> = mutableListOf()
    private val _videos: HashMap<String, ArrayList<Video>> = HashMap()
    private val _strms: NavigableMap<Int, Video> = TreeMap()
    private val _videoProgress: MutableList<VideoProgress> = mutableListOf()
    private val _creatorPages: HashMap<String, Int> = HashMap()
    private val _exhaustedCreators: HashMap<String, Boolean> = HashMap()
    private val _creatorNames: HashMap<String, String> = HashMap()

    @JvmField var subCount: Int = 0
    @JvmField var loadGeneration: Int = 0
    @JvmField var subsRetryCount: Int = 0
    @JvmField var paginationInFlight: Boolean = false

    // --- LiveData events ---
    private val _sessionExpired = MutableLiveData<Event<Unit>>()
    val sessionExpired: LiveData<Event<Unit>> = _sessionExpired

    private val _noSubscriptions = MutableLiveData<Event<Unit>>()
    val noSubscriptions: LiveData<Event<Unit>> = _noSubscriptions

    // --- Retry handler (lives entirely in the ViewModel) ---
    private val retryHandler = Handler(Looper.getMainLooper())

    // --- Public getters ---
    fun getSubscriptions(): MutableList<Subscription> = _subscriptions
    fun getVideos(): HashMap<String, ArrayList<Video>> = _videos
    fun getVideosFor(guid: String): ArrayList<Video>? = _videos[guid]
    fun getStrms(): NavigableMap<Int, Video> = _strms
    fun getVideoProgress(): MutableList<VideoProgress> = _videoProgress
    fun getCreatorPages(): HashMap<String, Int> = _creatorPages
    fun getExhaustedCreators(): HashMap<String, Boolean> = _exhaustedCreators
    fun getCreatorNames(): HashMap<String, String> = _creatorNames

    // --- Subscription loading ---

    fun refreshSubscriptions() {
        val gen = loadGeneration
        client.getSubs { subs ->
            if (loadGeneration != gen) return@getSubs
            if (subs == null) {
                // Retry a few times before signalling session expiry
                if (subsRetryCount < 3) {
                    subsRetryCount++
                    retryHandler.postDelayed({
                        if (loadGeneration == gen) {
                            refreshSubscriptions()
                        }
                    }, 3000)
                    return@getSubs
                }
                subsRetryCount = 0
                _sessionExpired.postValue(Event(Unit))
            } else {
                subsRetryCount = 0
                if (subs.isEmpty()) {
                    _noSubscriptions.postValue(Event(Unit))
                    return@getSubs
                }
                gotSubscriptions(subs)
            }
        }
    }

    private fun gotSubscriptions(subs: Array<Subscription>) {
        val trimmed = mutableListOf<Subscription>()
        for (sub in subs) {
            if (trimmed.isEmpty()) {
                trimmed.add(sub)
            } else {
                if (!containsSub(trimmed, sub)) trimmed.add(sub)
            }
        }
        _subscriptions.clear()
        _subscriptions.addAll(trimmed)
        val gen = loadGeneration
        var pending = 0
        for (sub in _subscriptions) {
            if (sub.creator != null) {
                pending++
                client.getLive(sub) { live ->
                    if (loadGeneration != gen) return@getLive
                    gotLiveInfo(sub, live)
                }
                client.getVideos(sub.creator!!, 1) { vids ->
                    if (loadGeneration != gen) return@getVideos
                    onVideosReady(sub.creator!!, vids)
                }
            }
        }
        subCount = pending
    }

    private fun containsSub(trimmed: List<Subscription>, sub: Subscription): Boolean {
        for (s in trimmed) {
            if (s.creator != null && s.creator == sub.creator) return true
        }
        return false
    }

    fun gotLiveInfo(sub: Subscription, live: Delivery) {
        if (live.groups == null || live.groups.isEmpty()
            || live.groups[0].origins == null || live.groups[0].origins.isEmpty()
            || live.groups[0].variants == null || live.groups[0].variants.isEmpty()
        ) return
        val l = live.groups[0].origins[0].url + live.groups[0].variants[0].url
        sub.streamUrl = l
        client.checkLive(l) { status ->
            sub.streaming = (status == 200)
        }
    }

    // Called from Fragment's onGridNearEnd as a pass-through (pagination still partly in Fragment for now)
    fun onVideosReady(creatorGUID: String, vids: Array<Video>?) {
        // Will be fully implemented in step 4; for now, delegate back to Fragment via callback
        _onVideosReadyCallback?.invoke(creatorGUID, vids)
    }

    // Temporary bridge: Fragment registers a callback so gotVideos() still works during step 3
    private var _onVideosReadyCallback: ((String, Array<Video>?) -> Unit)? = null

    fun setOnVideosReadyCallback(cb: (String, Array<Video>?) -> Unit) {
        _onVideosReadyCallback = cb
    }

    override fun onCleared() {
        super.onCleared()
        retryHandler.removeCallbacksAndMessages(null)
        _onVideosReadyCallback = null
    }
}
