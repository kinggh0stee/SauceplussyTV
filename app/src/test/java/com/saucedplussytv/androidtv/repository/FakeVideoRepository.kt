package com.saucedplussytv.androidtv.repository

import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoInfo
import com.saucedplussytv.androidtv.post.Post

class FakeVideoRepository : VideoRepository {

    // Controllable responses — set before calling the method under test
    var postResult: Post? = null
    var videoInfoResult: VideoInfo? = null
    // null = simulate error (callback gets original video back, which has empty vidUrl)
    var videoResult: Video? = null

    // Controls whether getPost invokes its callback immediately or captures it
    var capturePost: Boolean = false
    var capturedPostCallback: ((Post?) -> Unit)? = null

    override fun getPost(postId: String, callback: (Post?) -> Unit) {
        if (capturePost) {
            capturedPostCallback = callback
        } else {
            callback(postResult)
        }
    }

    override fun getVideoInfo(videoID: String, callback: (VideoInfo?) -> Unit) =
        callback(videoInfoResult)

    override fun getVideo(video: Video, res: String, callback: (Video) -> Unit) {
        // Simulate SaucedplussyTVClient.getVideo: on success sets vidUrl, on error returns
        // the original video unchanged (which has empty vidUrl by default).
        val result = videoResult ?: video
        callback(result)
    }
}
