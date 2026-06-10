package com.saucedplussytv.androidtv.repository

import com.saucedplussytv.androidtv.models.Delivery
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoProgress
import com.saucedplussytv.androidtv.subscription.Subscription

class FakeSubscriptionRepository : SubscriptionRepository {

    // null = simulate auth failure
    var subsResult: Array<Subscription>? = null
    var videosResult: Array<Video> = emptyArray()
    // empty Delivery (no groups) = no live stream
    var liveResult: Delivery = Delivery(emptyList())
    var checkLiveResult: Int = 404
    var progressResult: List<VideoProgress> = emptyList()

    override fun getSubs(callback: (Array<Subscription>?) -> Unit) = callback(subsResult)

    override fun getLive(sub: Subscription, callback: (Delivery) -> Unit) =
        callback(liveResult)

    override fun checkLive(url: String, callback: (Int) -> Unit) =
        callback(checkLiveResult)

    override fun getVideos(creatorGUID: String, page: Int, callback: (Array<Video>) -> Unit) =
        callback(videosResult)

    override fun getVideoProgress(ids: List<String>, callback: (List<VideoProgress>) -> Unit) =
        callback(progressResult)
}
