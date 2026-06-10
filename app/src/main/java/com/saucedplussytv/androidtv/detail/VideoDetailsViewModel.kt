package com.saucedplussytv.androidtv.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.saucedplussytv.androidtv.client.SaucedplussyTVClient
import com.saucedplussytv.androidtv.models.Level
import com.saucedplussytv.androidtv.models.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoDetailsViewModel @Inject constructor(
    private val client: SaucedplussyTVClient
) : ViewModel() {

    private val _levels = MutableLiveData<List<Level>>()
    val levels: LiveData<List<Level>> = _levels

    private val _dataError = MutableLiveData<String>()
    val dataError: LiveData<String> = _dataError

    fun loadLevels(postGuid: String) {
        client.getPost(postGuid) { post ->
            val attachments = post?.videoAttachments
            if (attachments.isNullOrEmpty()) {
                _dataError.postValue("Could not load video")
                return@getPost
            }
            val videoGuid = attachments[0].guid
            if (videoGuid.isEmpty()) return@getPost
            client.getVideoInfo(videoGuid) { videoInfo ->
                val levelList = videoInfo?.levels
                if (levelList == null) {
                    _dataError.postValue("Could not load video info")
                    return@getVideoInfo
                }
                _levels.postValue(levelList)
            }
        }
    }

    fun fetchVideoUrl(video: Video, resolution: String, onReady: (Video) -> Unit) {
        client.getVideo(video, resolution, onReady)
    }
}
