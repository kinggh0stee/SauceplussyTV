package com.saucedplussytv.androidtv.detail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import com.saucedplussytv.androidtv.models.Level
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.models.VideoInfo
import com.saucedplussytv.androidtv.post.Post
import com.saucedplussytv.androidtv.post.VideoAttachments
import com.saucedplussytv.androidtv.repository.FakeVideoRepository
import com.saucedplussytv.androidtv.util.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM-only unit tests for [VideoDetailsViewModel].
 *
 * No Hilt, no Robolectric, no emulator required. [FakeVideoRepository] invokes all callbacks
 * synchronously, and [InstantTaskExecutorRule] makes LiveData observers fire on the calling
 * thread, so every assertion can be made immediately after calling the method under test.
 */
class VideoDetailsViewModelTest {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    private lateinit var fakeRepo: FakeVideoRepository
    private lateinit var viewModel: VideoDetailsViewModel

    @Before
    fun setUp() {
        fakeRepo = FakeVideoRepository()
        viewModel = VideoDetailsViewModel(fakeRepo)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Reads the current LiveData value synchronously. Must only be called once per Event. */
    private fun <T> LiveData<T>.getOrAwait(): T {
        var value: T? = null
        observeForever { value = it }
        return value!!
    }

    private fun makeLevel(label: String): Level {
        val l = Level()
        l.label = label
        return l
    }

    private fun makePost(vararg guids: String): Post {
        val post = Post()
        post.id = "post-id"
        post.videoAttachments = guids.map { VideoAttachments(guid = it) }
        return post
    }

    private fun makeVideoInfo(levels: List<Level>?): VideoInfo {
        val info = VideoInfo()
        info.levels = levels
        return info
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `loadLevels happyPath emitsLevels`() {
        val expectedLevel = makeLevel("1080p")
        fakeRepo.postResult = makePost("vid-guid-1")
        fakeRepo.videoInfoResult = makeVideoInfo(listOf(expectedLevel))

        viewModel.loadLevels("post1")

        val event = viewModel.levels.getOrAwait()
        val levels = event.getContentIfNotHandled()
        assertNotNull("Expected levels event to be non-null", levels)
        assertEquals(1, levels!!.size)
        assertEquals("1080p", levels[0].label)
    }

    @Test
    fun `loadLevels nullPost emitsError`() {
        fakeRepo.postResult = null

        viewModel.loadLevels("post1")

        val event = viewModel.dataError.getOrAwait()
        val msg = event.getContentIfNotHandled()
        assertEquals("Could not load video", msg)
    }

    @Test
    fun `loadLevels emptyAttachments emitsError`() {
        val post = Post()
        post.videoAttachments = emptyList()
        fakeRepo.postResult = post

        viewModel.loadLevels("post1")

        val event = viewModel.dataError.getOrAwait()
        val msg = event.getContentIfNotHandled()
        assertEquals("Could not load video", msg)
    }

    @Test
    fun `loadLevels emptyGuid emitsError`() {
        fakeRepo.postResult = makePost("") // guid is blank

        viewModel.loadLevels("post1")

        val event = viewModel.dataError.getOrAwait()
        val msg = event.getContentIfNotHandled()
        assertEquals("Could not load video", msg)
    }

    @Test
    fun `loadLevels nullLevels emitsError`() {
        fakeRepo.postResult = makePost("vid-guid-1")
        fakeRepo.videoInfoResult = makeVideoInfo(null) // levels is null

        viewModel.loadLevels("post1")

        val event = viewModel.dataError.getOrAwait()
        val msg = event.getContentIfNotHandled()
        assertEquals("Could not load video info", msg)
    }

    @Test
    fun `loadLevels inFlightGuard secondCallIgnored`() {
        // Put getPost in capture mode so the first call never completes
        fakeRepo.capturePost = true
        fakeRepo.postResult = makePost("vid-guid-1")
        fakeRepo.videoInfoResult = makeVideoInfo(listOf(makeLevel("720p")))

        // First call: starts loading, suspends
        viewModel.loadLevels("post1")
        // Second call while first is in-flight: should be silently ignored
        viewModel.loadLevels("post1")

        // Let the first (and only) callback complete
        fakeRepo.capturePost = false
        fakeRepo.capturedPostCallback?.invoke(fakeRepo.postResult)

        // Collect all events emitted on the levels LiveData
        val emittedEvents = mutableListOf<Event<List<Level>>>()
        viewModel.levels.observeForever { emittedEvents.add(it) }

        // Only one event should exist (the second call was dropped by the in-flight guard)
        assertEquals(1, emittedEvents.size)
    }

    @Test
    fun `fetchVideoUrl success callsOnReady`() {
        val readyVideo = Video().apply { vidUrl = "https://cdn.example.com/video.m3u8" }
        fakeRepo.videoResult = readyVideo

        var receivedVideo: Video? = null
        viewModel.fetchVideoUrl(Video(), "1080") { v -> receivedVideo = v }

        assertNotNull("onReady should be called with a video", receivedVideo)
        assertEquals("https://cdn.example.com/video.m3u8", receivedVideo!!.vidUrl)
    }

    @Test
    fun `fetchVideoUrl nullUrl emitsError`() {
        // videoResult = null → fake returns original input video, which has empty vidUrl
        fakeRepo.videoResult = null

        viewModel.fetchVideoUrl(Video(), "1080") { /* onReady must not be called */ }

        val event = viewModel.dataError.getOrAwait()
        val msg = event.getContentIfNotHandled()
        assertEquals("Could not load video URL", msg)
    }

    @Test
    fun `fetchVideoUrl emptyUrl emitsError`() {
        // Explicitly set an empty vidUrl
        val emptyUrlVideo = Video().apply { vidUrl = "" }
        fakeRepo.videoResult = emptyUrlVideo

        var onReadyCalled = false
        viewModel.fetchVideoUrl(Video(), "1080") { onReadyCalled = true }

        assertNull("onReady must not be called when URL is empty",
            if (onReadyCalled) "called" else null)
        val event = viewModel.dataError.getOrAwait()
        assertEquals("Could not load video URL", event.getContentIfNotHandled())
    }
}
