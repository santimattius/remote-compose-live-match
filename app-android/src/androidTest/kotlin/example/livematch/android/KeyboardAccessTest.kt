package example.livematch.android

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The basic interaction the launcher keeps when there is no match must work without a touch screen. */
@RunWith(AndroidJUnit4::class)
class KeyboardAccessTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packaged = context.assets.open("scheduled-match.rce").use { it.readBytes() }
    private val originalSource = CandidateSources.current

    @After
    fun restore() {
        CandidateSources.current = originalSource
        File(context.filesDir, LAST_KNOWN_GOOD_SNAPSHOT_NAME).delete()
    }

    @Test
    fun retryIsReachedWithTabAndActivatedWithEnter() {
        File(context.filesDir, LAST_KNOWN_GOOD_SNAPSHOT_NAME).delete()
        CandidateSources.current = CandidateSource { null }

        ActivityScenario.launch(MainActivity::class.java).use {
            RemoteNodeTapper.awaitTextStartingWith(UNAVAILABLE)
            CandidateSources.current = CandidateSource { packaged }

            // Nothing has keyboard focus yet, so Enter alone must not do anything.
            RemoteNodeTapper.pressKey(KeyEvent.KEYCODE_ENTER)
            assertTrue("Enter without focus must not activate Retry", RemoteNodeTapper.isTextPresent(UNAVAILABLE))

            RemoteNodeTapper.pressKey(KeyEvent.KEYCODE_TAB)
            RemoteNodeTapper.pressKey(KeyEvent.KEYCODE_ENTER)

            RemoteNodeTapper.boundsOf("Remind me")
            assertFalse(RemoteNodeTapper.isTextPresent(UNAVAILABLE))
        }
    }

    private companion object {
        const val UNAVAILABLE = "Match information is unavailable right now."
    }
}
