package example.livematch.android

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import example.livematch.contract.ActionId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/**
 * TalkBack cannot focus the nodes inside the player, so each card also offers its actions as screen-reader
 * actions on the player view. Performing one must reach the host through the same allowlisted dispatcher as a tap.
 */
@RunWith(AndroidJUnit4::class)
class ScreenReaderActionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var previousServices = "null"
    private var previousEnabled = "0"

    @Before
    fun rememberAccessibilitySettings() {
        previousServices = shell("settings get secure enabled_accessibility_services").trim()
        previousEnabled = shell("settings get secure accessibility_enabled").trim()
    }

    @After
    fun restoreAccessibilitySettings() {
        if (previousServices.isEmpty() || previousServices == "null") {
            shell("settings delete secure enabled_accessibility_services")
        } else {
            shell("settings put secure enabled_accessibility_services $previousServices")
        }
        shell("settings put secure accessibility_enabled ${previousEnabled.takeIf { it != "null" } ?: "0"}")
    }

    @Test
    fun launcherCardOffersItsActionToScreenReaders() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            RemoteNodeTapper.boundsOf("Remind me")

            assertTrue("Expected the 'Set reminder' action", RemoteNodeTapper.performScreenReaderAction("Set reminder"))

            scenario.onActivity { activity ->
                assertEquals(ActionId.SET_REMINDER, activity.lastDispatchedAction)
                assertEquals(HostDestination.Reminder("scheduled-2027-01"), activity.lastDestination)
            }
        }
    }

    @Test
    fun liveCardOffersBothActionsToScreenReaders() {
        ActivityScenario.launch<DiagnosticPlaybackActivity>(liveIntent()).use { scenario ->
            RemoteNodeTapper.boundsOf("LIVE")

            assertTrue(RemoteNodeTapper.performScreenReaderAction("Open match"))
            scenario.onActivity { assertEquals(ActionId.OPEN_MATCH, it.lastDispatchedAction) }

            assertTrue(RemoteNodeTapper.performScreenReaderAction("View highlight"))
            scenario.onActivity { assertEquals(ActionId.OPEN_HIGHLIGHT, it.lastDispatchedAction) }
        }
    }

    @Test
    fun actionsAreStillOfferedWhileTalkBackIsRunning() {
        assumeTrue("TalkBack is not installed on this device", isInstalled(TALKBACK))
        // Without this grant TalkBack opens a permission dialog that covers the app.
        shell("pm grant $TALKBACK android.permission.POST_NOTIFICATIONS")
        shell("settings put secure enabled_accessibility_services $TALKBACK/$TALKBACK_SERVICE")
        shell("settings put secure accessibility_enabled 1")
        Thread.sleep(TALKBACK_START_MS)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            RemoteNodeTapper.boundsOf("Remind me")

            assertTrue("Expected the 'Set reminder' action with TalkBack running", RemoteNodeTapper.performScreenReaderAction("Set reminder"))

            scenario.onActivity { assertEquals(ActionId.SET_REMINDER, it.lastDispatchedAction) }
        }
    }

    private fun liveIntent(): Intent =
        Intent()
            .setComponent(ComponentName(instrumentation.targetContext, "example.livematch.android.DiagnosticPlaybackActivity"))
            .putExtra(DiagnosticPlaybackActivity.MODE_EXTRA, DiagnosticPlaybackActivity.MODE_PRE_ATTACH)
            .putExtra(DiagnosticPlaybackActivity.FIXTURE_EXTRA, DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME)

    private fun isInstalled(packageName: String): Boolean =
        try {
            instrumentation.targetContext.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).readBytes().decodeToString()
        }

    private companion object {
        const val TALKBACK = "com.google.android.marvin.talkback"
        const val TALKBACK_SERVICE = "com.google.android.marvin.talkback.TalkBackService"
        const val TALKBACK_START_MS = 4_000L
    }
}
