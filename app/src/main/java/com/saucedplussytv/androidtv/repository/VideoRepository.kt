package com.saucedplussytv.androidtv.repository

import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoInfo
import com.saucedplussytv.androidtv.post.Post

/** All callbacks are invoked on the main thread (Volley's ResponseDelivery contract). */
interface VideoRepository {
    fun getPost(postId: String, callback: (Post?) -> Unit)
    fun getVideoInfo(videoID: String, callback: (VideoInfo?) -> Unit)
    fun getVideo(video: Video, res: String, callback: (Video) -> Unit)
}
