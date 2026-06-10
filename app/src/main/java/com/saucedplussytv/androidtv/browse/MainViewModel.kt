package com.saucedplussytv.androidtv.browse

import androidx.lifecycle.ViewModel
import com.saucedplussytv.androidtv.authenticate.AuthManager
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient
import com.saucedplussytv.androidtv.client.SocketClient
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoProgress
import com.saucedplussytv.androidtv.subscription.Subscription
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

    // --- Public getters ---
    fun getSubscriptions(): MutableList<Subscription> = _subscriptions
    fun getVideos(): HashMap<String, ArrayList<Video>> = _videos
    fun getVideosFor(guid: String): ArrayList<Video>? = _videos[guid]
    fun getStrms(): NavigableMap<Int, Video> = _strms
    fun getVideoProgress(): MutableList<VideoProgress> = _videoProgress
    fun getCreatorPages(): HashMap<String, Int> = _creatorPages
    fun getExhaustedCreators(): HashMap<String, Boolean> = _exhaustedCreators
    fun getCreatorNames(): HashMap<String, String> = _creatorNames
}
