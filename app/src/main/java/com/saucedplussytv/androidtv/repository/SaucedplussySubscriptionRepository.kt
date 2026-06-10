package com.saucedplussytv.androidtv.repository

import com.saucedplussytv.androidtv.client.SaucedplussyTVClient
import com.saucedplussytv.androidtv.models.Delivery
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoProgress
import com.saucedplussytv.androidtv.subscription.Subscription
import javax.inject.Inject

class SaucedplussySubscriptionRepository @Inject constructor(
    private val client: SaucedplussyTVClient
) : SubscriptionRepository {

    override fun getSubs(callback: (Array<Subscription>?) -> Unit) =
        client.getSubs(callback)

    override fun getLive(sub: Subscription, callback: (Delivery) -> Unit) =
        client.getLive(sub, callback)

    override fun checkLive(url: String, callback: (Int) -> Unit) =
        client.checkLive(url, callback)

    override fun getVideos(creatorGUID: String, page: Int, callback: (Array<Video>) -> Unit) =
        client.getVideos(creatorGUID, page, callback)

    override fun getVideoProgress(ids: List<String>, callback: (List<VideoProgress>) -> Unit) =
        client.getVideoProgress(ids, callback)
}
