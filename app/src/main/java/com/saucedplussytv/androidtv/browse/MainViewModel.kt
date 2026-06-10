package com.saucedplussytv.androidtv.browse

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.saucedplussytv.androidtv.authenticate.AuthManager
import com.saucedplussytv.androidtv.client.SocketClient
import com.saucedplussytv.androidtv.models.Delivery
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoProgress
import com.saucedplussytv.androidtv.repository.SubscriptionRepository
import com.saucedplussytv.androidtv.subscription.Subscription
import com.saucedplussytv.androidtv.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.ArrayList
import java.util.HashMap
import java.util.NavigableMap
import java.util.TreeMap
import javax.inject.Inject

data class CreatorVideos(
    val creatorGUID: String,
    val isPagination: Boolean
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    // socketClient and authManager are injected for future use (socket sync, auth gating).
    // They are currently unused in MainViewModel's own logic — the Fragment wires them
    // directly. Nullable so that JVM unit tests can construct the VM without Android context.
    private val socketClient: SocketClient?,
    private val authManager: AuthManager?
) : ViewModel() {

    // --- Data model fields (moved from MainFragment) ---
    private val _subscriptions: MutableList<Subscription> = mutableListOf()
    private val _videos: HashMap<String, ArrayList<Video>> = HashMap()
    private val _strms: NavigableMap<Int, Video> = TreeMap()
    private val _videoProgress: MutableList<VideoProgress> = mutableListOf()
    private val _creatorPages: HashMap<String, Int> = HashMap()
    private val _exhaustedCreators: HashMap<String, Boolean> = HashMap()
    private val _creatorNames: HashMap<String, String> = HashMap()

    private var subCount: Int = 0
    private var loadGeneration: Int = 0
    private var subsRetryCount: Int = 0
    private var paginationInFlight: Boolean = false
    // True once the initial subscription batch has fully loaded; used to distinguish
    // initial-load from pagination calls inside gotVideos().
    private var initialBatchComplete: Boolean = false

    // --- LiveData events ---
    private val _sessionExpired = MutableLiveData<Event<Unit>>()
    val sessionExpired: LiveData<Event<Unit>> = _sessionExpired

    private val _noSubscriptions = MutableLiveData<Event<Unit>>()
    val noSubscriptions: LiveData<Event<Unit>> = _noSubscriptions

    private val _creatorVideosUpdated = MutableLiveData<Event<CreatorVideos>>()
    val creatorVideosUpdated: LiveData<Event<CreatorVideos>> = _creatorVideosUpdated

    // --- Retry handler (lives entirely in the ViewModel) ---
    // Lazy so that Looper.getMainLooper() is not called at construction time; JVM unit tests
    // that only exercise non-retry code paths never trigger this initializer.
    // For retry-path tests either use Robolectric or call refreshSubscriptions() with
    // subsRetryCount already at the limit so the postDelayed branch is never reached.
    private val retryHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    // --- Public getters ---
    fun getSubscriptions(): MutableList<Subscription> = _subscriptions
    fun getVideos(): HashMap<String, ArrayList<Video>> = _videos
    fun getVideosFor(guid: String): ArrayList<Video>? = _videos[guid]
    fun getMergedVideos(): List<Video> {
        val all = mutableListOf<Video>()
        for (vids in _videos.values) {
            if (vids != null) all.addAll(vids)
        }
        all.sortWith { a, b ->
            val da = a.releaseDate ?: ""
            val db = b.releaseDate ?: ""
            when {
                da.isEmpty() && db.isEmpty() -> 0
                da.isEmpty() -> 1
                db.isEmpty() -> -1
                else -> db.compareTo(da)
            }
        }
        return all
    }
    fun getStrms(): NavigableMap<Int, Video> = _strms
    fun getVideoProgress(): MutableList<VideoProgress> = _videoProgress
    fun getCreatorPages(): HashMap<String, Int> = _creatorPages
    fun getExhaustedCreators(): HashMap<String, Boolean> = _exhaustedCreators
    fun getCreatorNames(): HashMap<String, String> = _creatorNames

    // --- Entry point called by Fragment on login / refresh ---

    fun initialize() {
        subsRetryCount = 0
        refreshSubscriptions()
    }

    // --- Subscription loading ---

    fun refreshSubscriptions() {
        val gen = loadGeneration
        repository.getSubs { subs ->
            if (loadGeneration != gen) return@getSubs
            if (subs == null) {
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
                repository.getLive(sub) { live ->
                    if (loadGeneration != gen) return@getLive
                    gotLiveInfo(sub, live)
                }
                repository.getVideos(sub.creator!!, 1) { vids ->
                    if (loadGeneration != gen) return@getVideos
                    gotVideos(sub.creator!!, vids)
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

    private fun gotLiveInfo(sub: Subscription, live: Delivery) {
        val groups = live.groups ?: return
        if (groups.isEmpty()) return
        val group = groups[0]
        val origins = group.origins ?: return
        if (origins.isEmpty()) return
        val variants = group.variants ?: return
        if (variants.isEmpty()) return
        val l = origins[0].url + variants[0].url
        sub.streamUrl = l
        val gen = loadGeneration
        repository.checkLive(l) { status ->
            if (loadGeneration != gen) return@checkLive
            sub.streaming = (status == 200)
        }
    }

    // --- Video model mutation (model half of gotVideos) ---

    fun gotVideos(creatorGUID: String, vids: Array<Video>) {

        // Extract creator name
        if (vids.isNotEmpty() && vids[0].creator != null
            && !_creatorNames.containsKey(creatorGUID)
        ) {
            val name = vids[0].creator!!.title
            if (name.isNotEmpty()) _creatorNames[creatorGUID] = name
        }

        val existing = _videos[creatorGUID]
        val isPagination = initialBatchComplete && existing != null && existing.isNotEmpty()

        if (existing != null && existing.isNotEmpty()) {
            existing.addAll(vids.toList())
        } else {
            _videos[creatorGUID] = ArrayList(vids.toList())
        }

        if (isPagination) {
            paginationInFlight = false
            _creatorVideosUpdated.postValue(Event(CreatorVideos(creatorGUID, isPagination = true)))
        } else {
            subCount--
            _creatorVideosUpdated.postValue(Event(CreatorVideos(creatorGUID, isPagination = false)))
            if (subCount <= 0) {
                initialBatchComplete = true
                fetchProgressAsync()
            }
        }
    }

    // --- Pagination ---

    fun loadNextPage() {
        if (paginationInFlight || _subscriptions.isEmpty()) return

        var dispatchCount = 0
        for (sub in _subscriptions) {
            val creator = sub.creator
            if (creator != null && !(_exhaustedCreators[creator] == true)) dispatchCount++
        }
        if (dispatchCount == 0) return

        paginationInFlight = true
        val gen = loadGeneration
        val remaining = java.util.concurrent.atomic.AtomicInteger(dispatchCount)

        for (sub in _subscriptions) {
            val creator = sub.creator ?: continue
            if (_exhaustedCreators[creator] == true) continue
            val nextPage = (_creatorPages[creator] ?: 1) + 1
            repository.getVideos(creator, nextPage) { vids ->
                if (loadGeneration != gen) {
                    if (remaining.decrementAndGet() == 0) paginationInFlight = false
                    return@getVideos
                }
                if (vids.isEmpty()) {
                    _exhaustedCreators[creator] = true
                } else {
                    _creatorPages[creator] = nextPage
                }
                gotVideos(creator, vids)
                if (remaining.decrementAndGet() == 0) paginationInFlight = false
            }
        }
    }

    // --- Video progress ---

    private fun fetchProgressAsync() {
        val ids = _videos.values.flatMap { it }.map { it.id }
        repository.getVideoProgress(ids) { progress ->
            _videoProgress.clear()
            _videoProgress.addAll(progress)
            _videoProgressUpdated.postValue(Unit)
        }
    }

    fun refreshVideoProgress() {
        fetchProgressAsync()
    }

    private val _videoProgressUpdated = MutableLiveData<Unit>()
    val videoProgressUpdated: LiveData<Unit> = _videoProgressUpdated

    // --- Cleanup ---

    fun clearForLogout() {
        retryHandler.removeCallbacksAndMessages(null)
        loadGeneration++
        _subscriptions.clear()
        _videos.clear()
        _strms.clear()
        _videoProgress.clear()
        _creatorPages.clear()
        _exhaustedCreators.clear()
        _creatorNames.clear()
        subCount = 0
        subsRetryCount = 0
        paginationInFlight = false
        initialBatchComplete = false
    }

    override fun onCleared() {
        super.onCleared()
        retryHandler.removeCallbacksAndMessages(null)
    }

}
