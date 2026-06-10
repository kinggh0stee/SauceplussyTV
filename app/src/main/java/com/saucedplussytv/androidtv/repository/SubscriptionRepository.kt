package com.saucedplussytv.androidtv.repository

import com.saucedplussytv.androidtv.models.Delivery
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoProgress
import com.saucedplussytv.androidtv.subscription.Subscription

interface SubscriptionRepository {
    fun getSubs(callback: (Array<Subscription>?) -> Unit)
    fun getLive(sub: Subscription, callback: (Delivery) -> Unit)
    fun checkLive(url: String, callback: (Int) -> Unit)
    fun getVideos(creatorGUID: String, page: Int, callback: (Array<Video>) -> Unit)
    fun getVideoProgress(ids: List<String>, callback: (List<VideoProgress>) -> Unit)
}
