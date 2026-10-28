package example.livematch.android

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import example.livematch.contract.MatchPresentationCodec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The launcher must never show a blank player. These tests drive the real activity on a device with
 * the candidate envelope replaced to simulate being offline or receiving a bad update, and assert
 * what the user sees and what ends up in the last-known-good cache.
 */
@RunWith(AndroidJUnit4::class)
class OfflineFallbackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val snapshot = File(context.filesDir, LAST_KNOWN_GOOD_SNAPSHOT_NAME)
    private val packaged = context.assets.open("scheduled-match.rce").use { it.readBytes() }
    private val originalSource = CandidateSources.current

    @Before
    fun startWithoutACache() {
        snapshot.delete()
    }

    @After
    fun restoreProductionBehavior() {
        CandidateSources.current = originalSource
        snapshot.delete()
    }

    @Test
    fun offlineLaunchPlaysTheLastKnownGoodMatch() {
        CandidateSources.current = CandidateSource { packaged }
        launchAndAwaitMatch()
        assertTrue("A played match should have been cached", snapshot.isFile)

        CandidateSources.current = CandidateSource { null }
        launchAndAwaitMatch()

        assertFalse(RemoteNodeTapper.isTextPresent(FALLBACK_TEXT))
    }

    @Test
    fun rejectedUpdateKeepsThePlayingMatchAndTheCache() {
        CandidateSources.current = CandidateSource { packaged }
        launchAndAwaitMatch()
        val cached = snapshot.readBytes()

        CandidateSources.current = CandidateSource { tampered(packaged) }
        launchAndAwaitMatch()

        assertArrayEquals("A rejected update must not change the cache", cached, snapshot.readBytes())
    }

    @Test
    fun noCandidateAndNoCacheShowsTheLocalFallback() {
        CandidateSources.current = CandidateSource { null }

        launchAndAwaitFallback()

        assertFalse(RemoteNodeTapper.isTextPresent("Remind me"))
        assertFalse("Nothing playable should be cached", snapshot.exists())
    }

    @Test
    fun rejectedUpdateWithoutACacheShowsTheLocalFallback() {
        CandidateSources.current = CandidateSource { tampered(packaged) }

        launchAndAwaitFallback()

        assertFalse("A rejected update must never be cached", snapshot.exists())
    }

    @Test
    fun corruptCacheShowsTheLocalFallbackInsteadOfCrashing() {
        snapshot.writeBytes(byteArrayOf(1, 2, 3))
        CandidateSources.current = CandidateSource { null }

        launchAndAwaitFallback()

        assertFalse(RemoteNodeTapper.isTextPresent("Remind me"))
    }

    @Test
    fun aCachedLiveMatchThatWentStaleIsNotShownAsCurrent() {
        // The live fixture was created in 2023, far beyond the limit for showing a live score as current.
        cacheFixture("live-match.rce")
        CandidateSources.current = CandidateSource { null }

        launchAndAwaitText(STALE_TEXT)

        assertFalse(RemoteNodeTapper.isTextPresent("LIVE"))
        assertFalse(RemoteNodeTapper.isTextPresent(FALLBACK_TEXT))
    }

    @Test
    fun aCachedFinishedMatchStillPlaysOfflineBecauseItDoesNotAge() {
        cacheFixture("finished-match.rce")
        CandidateSources.current = CandidateSource { null }

        ActivityScenario.launch(MainActivity::class.java).use { RemoteNodeTapper.boundsOf("View summary") }

        assertFalse(RemoteNodeTapper.isTextPresent(STALE_TEXT))
    }

    @Test
    fun theFallbackOffersARetryThatPlaysTheMatchOnceItIsAvailable() {
        CandidateSources.current = CandidateSource { null }

        ActivityScenario.launch(MainActivity::class.java).use {
            RemoteNodeTapper.awaitTextStartingWith(FALLBACK_TEXT)
            CandidateSources.current = CandidateSource { packaged }

            RemoteNodeTapper.tapText(RETRY_TEXT)

            RemoteNodeTapper.boundsOf("Remind me")
            assertFalse(RemoteNodeTapper.isTextPresent(FALLBACK_TEXT))
        }
        assertTrue("The recovered match should have been cached", snapshot.isFile)
    }

    @Test
    fun theOutOfDateMessageOffersARetryToo() {
        cacheFixture("live-match.rce")
        CandidateSources.current = CandidateSource { null }

        ActivityScenario.launch(MainActivity::class.java).use {
            RemoteNodeTapper.awaitTextStartingWith(STALE_TEXT)
            CandidateSources.current = CandidateSource { packaged }

            RemoteNodeTapper.tapText(RETRY_TEXT)

            RemoteNodeTapper.boundsOf("Remind me")
            assertFalse(RemoteNodeTapper.isTextPresent(STALE_TEXT))
        }
    }

    @Test
    fun retryKeepsBeingOfferedWhileTheMatchIsStillUnavailable() {
        CandidateSources.current = CandidateSource { null }

        ActivityScenario.launch(MainActivity::class.java).use {
            RemoteNodeTapper.awaitTextStartingWith(FALLBACK_TEXT)

            RemoteNodeTapper.tapText(RETRY_TEXT)

            RemoteNodeTapper.awaitTextStartingWith(FALLBACK_TEXT)
            assertTrue("Retry should still be offered", RemoteNodeTapper.isTextPresent(RETRY_TEXT))
        }
    }

    @Test
    fun retryIsAnImportantFocusableButtonForScreenReaders() {
        CandidateSources.current = CandidateSource { null }

        ActivityScenario.launch(MainActivity::class.java).use {
            // The text is a child of the button, so look at the nearest node that can be clicked.
            val button = generateSequence(RemoteNodeTapper.nodeShowing(RETRY_TEXT)) { it.parent }.firstOrNull { it.isClickable }

            assertTrue("Retry must sit inside a clickable node", button != null)
            // The nodes inside the player are published as not important, which hides them from TalkBack.
            assertTrue("The Retry button must be important for accessibility", button!!.isImportantForAccessibility)
            assertTrue("The Retry button must be focusable", button.isFocusable)
            assertTrue("The Retry button must be visible to the user", button.isVisibleToUser)
        }
    }

    private fun launchAndAwaitMatch() {
        ActivityScenario.launch(MainActivity::class.java).use { RemoteNodeTapper.boundsOf("Remind me") }
    }

    private fun launchAndAwaitText(text: String) {
        ActivityScenario.launch(MainActivity::class.java).use { RemoteNodeTapper.awaitTextStartingWith(text) }
    }

    /** Writes a validated copy of a packaged fixture into the cache, as if it had been played earlier. */
    private fun cacheFixture(assetName: String) {
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        val loaded = EnvelopeMatchLoader().load(bytes, Instant.now().epochSecond) as MatchEnvelopeLoadResult.Loaded
        val match =
            ValidatedMatch(
                SnapshotMetadata(loaded.presentation.matchId, loaded.revision, loaded.createdAtEpochSeconds, loaded.expiresAtEpochSeconds),
                MatchPresentationCodec.encode(loaded.presentation),
            )
        snapshot.writeBytes(SnapshotCodec.encode(match))
    }

    private fun launchAndAwaitFallback() {
        ActivityScenario.launch(MainActivity::class.java).use { RemoteNodeTapper.awaitTextStartingWith(FALLBACK_TEXT) }
    }

    /** Flips one byte in the middle of the envelope and proves the loader rejects the result. */
    private fun tampered(envelope: ByteArray): ByteArray {
        val bytes = envelope.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0xFF).toByte() }
        val result = EnvelopeMatchLoader().load(bytes, Instant.now().epochSecond)
        assertFalse("The tampered envelope must be rejected, but loaded as ", result is MatchEnvelopeLoadResult.Loaded)
        return bytes
    }

    private companion object {
        const val FALLBACK_TEXT = "Match information is unavailable right now."
        const val STALE_TEXT = "Match information is out of date."
        const val RETRY_TEXT = "Retry"
    }
}
