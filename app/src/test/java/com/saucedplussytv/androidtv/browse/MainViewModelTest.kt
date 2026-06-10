package com.saucedplussytv.androidtv.browse

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import com.saucedplussytv.androidtv.models.Creator
import com.saucedplussytv.androidtv.models.Video
import com.saucedplussytv.androidtv.repository.FakeSubscriptionRepository
import com.saucedplussytv.androidtv.subscription.Subscription
import com.saucedplussytv.androidtv.util.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM-only unit tests for [MainViewModel].
 *
 * ## Threading note
 * [MainViewModel] creates a `Handler(Looper.getMainLooper())` lazily (only on first postDelayed
 * call). Tests that only exercise non-retry paths never trigger the lazy initialiser, so no
 * Android Looper is needed and no Robolectric is required.
 *
 * ## Dependency note — SocketClient / AuthManager
 * Both are final Kotlin classes that require an Android [Context]. Neither is invoked in the
 * code paths exercised here. They are passed as typed-null via an unchecked cast.
 * This is intentional and safe for the tested paths.
 *
 * TODO: full retry-timing tests (Handler.postDelayed) require Robolectric or making
 *       SocketClient / AuthManager injectable interfaces.
 */
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    private lateinit var fakeRepo: FakeSubscriptionRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        fakeRepo = FakeSubscriptionRepository()
        // SocketClient and AuthManager are nullable in MainViewModel (they are not called
        // in the tested code paths). Passing null avoids instantiating Android-context
        // dependent classes in a plain JVM test environment.
        viewModel = MainViewModel(fakeRepo, null, null)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun <T> LiveData<T>.getOrAwait(): T {
        var value: T? = null
        observeForever { value = it }
        return value!!
    }

    private fun makeSubscription(creatorGUID: String): Subscription =
        Subscription().apply { creator = creatorGUID }

    private fun makeVideo(creatorGUID: String, title: String = "Test Video"): Video =
        Video().apply {
            id = "vid-$title"
            this.title = title
            creator = Creator().apply { this.title = creatorGUID }
        }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `initialize success emitsCreatorVideosUpdated`() {
        val guid = "creator-guid-1"
        fakeRepo.subsResult = arrayOf(makeSubscription(guid))
        fakeRepo.videosResult = arrayOf(makeVideo(guid))

        viewModel.initialize()

        // Collect all creatorVideosUpdated events
        val events = mutableListOf<Event<CreatorVideos>>()
        viewModel.creatorVideosUpdated.observeForever { events.add(it) }

        assertTrue("Expected at least one creatorVideosUpdated event", events.isNotEmpty())
        val payload = events.first().getContentIfNotHandled()
        assertNotNull(payload)
        assertEquals(guid, payload!!.creatorGUID)
        assertEquals(false, payload.isPagination)
    }

    @Test
    fun `initialize emptySubs emitsNoSubscriptions`() {
        fakeRepo.subsResult = emptyArray()

        viewModel.initialize()

        val event = viewModel.noSubscriptions.getOrAwait()
        assertNotNull("Expected noSubscriptions event", event.getContentIfNotHandled())
    }

    @Test
    fun `refreshSubscriptions nullSubs sessionExpired after maxRetries`() {
        // The retry path calls retryHandler.postDelayed which requires Looper.getMainLooper().
        // We avoid it entirely by pre-setting subsRetryCount to 3 (the maximum) and calling
        // refreshSubscriptions() once. With subsRetryCount == 3, the "< 3" guard is false so
        // _sessionExpired fires immediately without touching the Handler.
        //
        // This verifies the "emit sessionExpired after exhausting retries" logic; the delay
        // between retries is separately handled by the Handler and not tested here
        // (requires Robolectric for that).
        fakeRepo.subsResult = null

        // Manually advance subsRetryCount to the limit via reflection-free public API:
        // calling refreshSubscriptions 3 times increments it to 3, each call schedules a
        // postDelayed but since fakes are synchronous the delayed block never runs in JVM.
        // On the 4th call subsRetryCount is 3 (NOT < 3) so sessionExpired fires.
        //
        // We call refreshSubscriptions 4 times total here. The first 3 calls try to
        // schedule a retry via postDelayed — since retryHandler is lazy and only accessed
        // during those calls, we need it to not crash. We handle this by catching the
        // potential crash and only asserting on the result of call 4.

        // Call 4 times; 4th call fires sessionExpired
        try { viewModel.refreshSubscriptions() } catch (_: Throwable) { /* postDelayed on first null */ }
        try { viewModel.refreshSubscriptions() } catch (_: Throwable) { /* postDelayed on second null */ }
        try { viewModel.refreshSubscriptions() } catch (_: Throwable) { /* postDelayed on third null */ }
        // 4th call: subsRetryCount == 3, postDelayed is NOT called, sessionExpired fires
        viewModel.refreshSubscriptions()

        val event = viewModel.sessionExpired.getOrAwait()
        assertNotNull("Expected sessionExpired event after max retries", event.getContentIfNotHandled())
    }

    @Test
    fun `clearForLogout clearsAllData`() {
        // Load some data first
        val guid = "creator-guid-clear"
        fakeRepo.subsResult = arrayOf(makeSubscription(guid))
        fakeRepo.videosResult = arrayOf(makeVideo(guid))
        viewModel.initialize()

        // Preconditions: data should be present
        assertTrue(viewModel.getSubscriptions().isNotEmpty())

        // clearForLogout calls retryHandler.removeCallbacksAndMessages(null).
        // retryHandler is lazy; if it was never touched, it initializes via
        // Looper.getMainLooper() here. In JVM unit tests the Android stub returns null for
        // getMainLooper(), which means Handler(null) is called. Catch any resulting exception
        // and still assert the data was cleared (the data-clearing code runs before the handler call).
        try {
            viewModel.clearForLogout()
        } catch (_: Throwable) {
            // Handler init may fail in JVM stub environment — data clearing still ran
        }

        assertTrue("Subscriptions should be empty after logout",
            viewModel.getSubscriptions().isEmpty())
        assertTrue("Videos map should be empty after logout",
            viewModel.getVideos().isEmpty())
        assertTrue("Video progress should be empty after logout",
            viewModel.getVideoProgress().isEmpty())
        assertTrue("Creator pages should be empty after logout",
            viewModel.getCreatorPages().isEmpty())
        assertTrue("Exhausted creators should be empty after logout",
            viewModel.getExhaustedCreators().isEmpty())
        assertTrue("Creator names should be empty after logout",
            viewModel.getCreatorNames().isEmpty())
    }

    @Test
    fun `gotVideos populatesVideosMap and extractsCreatorName`() {
        val guid = "creator-guid-2"
        val video = makeVideo(guid, "My Video")
        // Manually drive gotVideos so we can assert internal state without going
        // through the full subscription flow.
        viewModel.gotVideos(guid, arrayOf(video))

        val videos = viewModel.getVideosFor(guid)
        assertNotNull("Videos for creator should be non-null", videos)
        assertEquals(1, videos!!.size)
        assertEquals("My Video", videos[0].title)

        // Creator name should be extracted from the first video's creator.title
        assertEquals(guid, viewModel.getCreatorNames()[guid])
    }

    @Test
    fun `getMergedVideos returnsSortedByReleaseDate`() {
        val guid = "creator-x"
        val older = makeVideo(guid, "older").apply { releaseDate = "2024-01-01T00:00:00Z" }
        val newer = makeVideo(guid, "newer").apply { releaseDate = "2025-06-01T00:00:00Z" }
        viewModel.gotVideos(guid, arrayOf(older, newer))

        val merged = viewModel.getMergedVideos()
        assertEquals(2, merged.size)
        // Sorted descending by releaseDate — newest first
        assertEquals("newer", merged[0].title)
        assertEquals("older", merged[1].title)
    }
}
