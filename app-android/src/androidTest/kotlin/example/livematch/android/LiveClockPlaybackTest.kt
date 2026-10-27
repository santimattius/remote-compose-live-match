package example.livematch.android

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveClockPlaybackTest {
    @Test
    fun liveMatchClockStartsAtTheMatchClockAndAdvancesLocally() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent()
                .setComponent(ComponentName(context, "example.livematch.android.DiagnosticPlaybackActivity"))
                .putExtra(DiagnosticPlaybackActivity.MODE_EXTRA, DiagnosticPlaybackActivity.MODE_PRE_ATTACH)
                .putExtra(DiagnosticPlaybackActivity.FIXTURE_EXTRA, DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME)

        ActivityScenario.launch<DiagnosticPlaybackActivity>(intent).use {
            val first = clockSeconds(RemoteNodeTapper.awaitTextStartingWith(PERIOD))
            Thread.sleep(OBSERVATION_MS)
            val second = clockSeconds(RemoteNodeTapper.awaitTextStartingWith(PERIOD))

            assertTrue("Clock starts at the match clock ($FIXTURE_CLOCK_SECONDS) but was $first", first in FIXTURE_CLOCK_SECONDS..FIXTURE_CLOCK_SECONDS + LAUNCH_ALLOWANCE_S)
            assertTrue("Clock should advance by about ${OBSERVATION_MS / 1000}s locally: $first -> $second", second - first in 1..OBSERVATION_MS / 1000 + 2)
        }
    }

    private fun clockSeconds(text: String): Int {
        val (minutes, seconds) = text.removePrefix(PERIOD).trim().split(":").map(String::toInt)
        return minutes * 60 + seconds
    }

    private companion object {
        const val PERIOD = "2nd Half"
        const val FIXTURE_CLOCK_SECONDS = 67 * 60 + 12
        const val LAUNCH_ALLOWANCE_S = 30
        const val OBSERVATION_MS = 3_000L
    }
}
