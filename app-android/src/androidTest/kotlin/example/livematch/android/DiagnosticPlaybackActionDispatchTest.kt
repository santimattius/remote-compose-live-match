package example.livematch.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import example.livematch.contract.ActionId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticPlaybackActionDispatchTest {
    @Test
    fun tappingBothLiveTargetsDispatchesOpenMatchAndOpenHighlightThroughTheAllowlist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = diagnosticIntent(context, DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME)

        ActivityScenario.launch<DiagnosticPlaybackActivity>(intent).use { scenario ->
            val expected = mapOf("Open match" to ActionId.OPEN_MATCH, "View highlight" to ActionId.OPEN_HIGHLIGHT)

            expected.forEach { (label, actionId) ->
                RemoteNodeTapper.tapText(label)
                scenario.onActivity { activity -> assertEquals("Tapping '$label'", actionId, activity.lastDispatchedAction) }
            }
        }
    }

    @Test
    fun tappingTheFinishedTargetDispatchesOpenSummaryThroughTheAllowlist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = diagnosticIntent(context, DiagnosticPlaybackActivity.FINISHED_MATCH_FIXTURE_NAME)

        ActivityScenario.launch<DiagnosticPlaybackActivity>(intent).use { scenario ->
            RemoteNodeTapper.tapText("View summary")

            scenario.onActivity { activity -> assertEquals(ActionId.OPEN_SUMMARY, activity.lastDispatchedAction) }
        }
    }

    private fun diagnosticIntent(
        context: Context,
        fixture: String,
    ): Intent =
        Intent()
            .setComponent(ComponentName(context, "example.livematch.android.DiagnosticPlaybackActivity"))
            .putExtra(DiagnosticPlaybackActivity.MODE_EXTRA, DiagnosticPlaybackActivity.MODE_PRE_ATTACH)
            .putExtra(DiagnosticPlaybackActivity.FIXTURE_EXTRA, fixture)
}
