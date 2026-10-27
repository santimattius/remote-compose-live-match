package example.livematch.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.view.ViewGroup
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class DiagnosticPlaybackTest {
    @Test
    fun exactDiagnosticFixturesInstallIntoResumedPlayerThroughExpectedAttachmentPaths() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures =
            listOf(
                Fixture(DiagnosticPlaybackActivity.PAINT_PROBE_FIXTURE_NAME, PAINT_PROBE_SHA256),
                Fixture(DiagnosticPlaybackActivity.ALPHA18_API6_CONTROL_FIXTURE_NAME, ALPHA18_API6_CONTROL_SHA256),
                Fixture(DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME, LIVE_MATCH_SHA256),
                Fixture(DiagnosticPlaybackActivity.FINISHED_MATCH_FIXTURE_NAME, FINISHED_MATCH_SHA256),
            )

        fixtures.forEach { fixture ->
            val bytes = context.assets.open(fixture.name).use { it.readBytes() }
            assertEquals(fixture.sha256, sha256(bytes))
            launchAndAssertPlayer(context, DiagnosticPlaybackActivity.MODE_POST_ATTACH, fixture.name)
        }
        launchAndAssertPlayer(
            context,
            DiagnosticPlaybackActivity.MODE_PRE_ATTACH,
            DiagnosticPlaybackActivity.PAINT_PROBE_FIXTURE_NAME,
        )
    }

    private fun launchAndAssertPlayer(
        context: android.content.Context,
        mode: String,
        fixture: String,
    ) {
        val intent =
            Intent()
                .setComponent(ComponentName(context, DIAGNOSTIC_ACTIVITY))
                .putExtra(DiagnosticPlaybackActivity.MODE_EXTRA, mode)
                .putExtra(DiagnosticPlaybackActivity.FIXTURE_EXTRA, fixture)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val player = findRemoteComposePlayer(root)
                assertTrue(player != null)
                assertTrue(player!!.width > 0 && player.height > 0)
            }
        }
    }

    private fun findRemoteComposePlayer(view: android.view.View): RemoteComposePlayer? {
        if (view is RemoteComposePlayer) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findRemoteComposePlayer(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Fixture(
        val name: String,
        val sha256: String,
    )

    private companion object {
        const val DIAGNOSTIC_ACTIVITY = "example.livematch.android.DiagnosticPlaybackActivity"
        const val PAINT_PROBE_SHA256 = "4a3b1f4c4fe2c0c00153fa4655bfc2ada94564abd1fde6edfd0bcf4ffe0011e6"
        const val ALPHA18_API6_CONTROL_SHA256 = "5474bfb60c9bd9329116c5e3d0ab407f00efae516e428f41174a35896dbce234"
        const val LIVE_MATCH_SHA256 = "ef378792c2554764d3a1b0e1a15b911cc11f3591673e4a10e3210cf1981c873e"
        const val FINISHED_MATCH_SHA256 = "b9b256966bc9716d80039cfdcee2d68d429ab61b89e4dd54bffb693598e5fabf"
    }
}
