package example.livematch.android

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityDescriptionsTest {
    @Test
    fun liveMatchActionsAndStatusAreAnnouncedWithMeaningfulDescriptions() {
        launchDiagnostic(DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME).use {
            assertEquals(
                "Live match status. The live indicator pulses locally.",
                RemoteNodeTapper.descriptionOf("LIVE"),
            )
            assertTrue(RemoteNodeTapper.descriptionOf("Open match").startsWith("Open live match "))
            assertTrue(RemoteNodeTapper.descriptionOf("View highlight").startsWith("View highlight for "))
            assertEquals(
                LiveMatchRemotePresentation.detailsActionDescription,
                RemoteNodeTapper.descriptionOf(LiveMatchRemotePresentation.detailsActionDescription),
            )
        }
    }

    @Test
    fun finishedAndScheduledActionsAreAnnouncedWithMeaningfulDescriptions() {
        launchDiagnostic(DiagnosticPlaybackActivity.FINISHED_MATCH_FIXTURE_NAME).use {
            assertTrue(RemoteNodeTapper.descriptionOf("View summary").startsWith("Open summary for "))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(RemoteNodeTapper.descriptionOf("Remind me").startsWith("Set reminder for Orcas versus Tigers"))
        }
    }

    private fun launchDiagnostic(fixture: String): ActivityScenario<DiagnosticPlaybackActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent()
                .setComponent(ComponentName(context, "example.livematch.android.DiagnosticPlaybackActivity"))
                .putExtra(DiagnosticPlaybackActivity.MODE_EXTRA, DiagnosticPlaybackActivity.MODE_PRE_ATTACH)
                .putExtra(DiagnosticPlaybackActivity.FIXTURE_EXTRA, fixture)
        return ActivityScenario.launch(intent)
    }
}
