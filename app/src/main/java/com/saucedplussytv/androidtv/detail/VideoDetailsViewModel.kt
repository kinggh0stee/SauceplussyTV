package com.saucedplussytv.androidtv.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.saucedplussytv.androidtv.models.Level
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.repository.VideoRepository
import com.saucedplussytv.androidtv.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoDetailsViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _levels = MutableLiveData<Event<List<Level>>>()
    val levels: LiveData<Event<List<Level>>> = _levels

    private val _dataError = MutableLiveData<Event<String>>()
    val dataError: LiveData<Event<String>> = _dataError

    private var isLoadingLevels = false

    fun loadLevels(postGuid: String) {
        if (isLoadingLevels) return
        isLoadingLevels = true
        repository.getPost(postGuid) { post ->
            val attachments = post?.videoAttachments
            if (attachments.isNullOrEmpty()) {
                _dataError.postValue(Event("Could not load video"))
                isLoadingLevels = false
                return@getPost
            }
            val videoGuid = attachments[0].guid
            if (videoGuid.isEmpty()) {
                _dataError.postValue(Event("Could not load video"))
                isLoadingLevels = false
                return@getPost
            }
            repository.getVideoInfo(videoGuid) { videoInfo ->
                isLoadingLevels = false
                val levelList = videoInfo?.levels
                if (levelList == null) {
                    _dataError.postValue(Event("Could not load video info"))
                    return@getVideoInfo
                }
                _levels.postValue(Event(levelList))
            }
        }
    }

    fun fetchVideoUrl(video: Video, resolution: String, onReady: (Video) -> Unit) {
        repository.getVideo(video, resolution) { result ->
            if (result.vidUrl.isNullOrEmpty()) {
                _dataError.postValue(Event("Could not load video URL"))
            } else {
                onReady(result)
            }
        }
    }
}
