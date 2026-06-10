package com.saucedplussytv.androidtv.repository

import com.saucedplussytv.androidtv.client.SaucedplussyTVClient
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoInfo
import com.saucedplussytv.androidtv.post.Post
import javax.inject.Inject

class SaucedplussyVideoRepository @Inject constructor(
    private val client: SaucedplussyTVClient
) : VideoRepository {

    override fun getPost(postId: String, callback: (Post?) -> Unit) =
        client.getPost(postId, callback)

    override fun getVideoInfo(videoID: String, callback: (VideoInfo?) -> Unit) =
        client.getVideoInfo(videoID, callback)

    override fun getVideo(video: Video, res: String, callback: (Video) -> Unit) =
        client.getVideo(video, res, callback)
}
