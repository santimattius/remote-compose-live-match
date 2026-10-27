package example.livematch.android

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class ReducedMotionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var previousScale = "null"

    @Before
    fun rememberAnimationScale() {
        previousScale = shell("settings get global $SETTING").trim()
    }

    @After
    fun restoreAnimationScale() {
        if (previousScale.isEmpty() || previousScale == "null") {
            shell("settings delete global $SETTING")
        } else {
            shell("settings put global $SETTING $previousScale")
        }
    }

    @Test
    fun liveIndicatorStaysStillWhenTheUserRemovedAnimations() {
        shell("settings put global $SETTING 0")

        assertEquals("The LIVE indicator must not pulse", 1, distinctLiveIndicatorFrames())
    }

    @Test
    fun liveIndicatorPulsesWhenAnimationsAreEnabled() {
        shell("settings put global $SETTING 1")

        assertTrue("Control: the LIVE indicator should pulse", distinctLiveIndicatorFrames() > 1)
    }

    /** Samples the LIVE indicator's pixels over time and counts how many different frames it showed. */
    private fun distinctLiveIndicatorFrames(): Int {
        val context = instrumentation.targetContext
        val intent =
            Intent()
                .setComponent(ComponentName(context, "example.livematch.android.DiagnosticPlaybackActivity"))
                .putExtra(DiagnosticPlaybackActivity.MODE_EXTRA, DiagnosticPlaybackActivity.MODE_PRE_ATTACH)
                .putExtra(DiagnosticPlaybackActivity.FIXTURE_EXTRA, DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME)
        return ActivityScenario.launch<DiagnosticPlaybackActivity>(intent).use {
            val bounds = RemoteNodeTapper.boundsOf("LIVE")
            val frames = mutableListOf<Bitmap>()
            repeat(SAMPLES) {
                val screen = instrumentation.uiAutomation.takeScreenshot()
                val crop = Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
                if (frames.none { it.sameAs(crop) }) frames += crop
                Thread.sleep(SAMPLE_INTERVAL_MS)
            }
            frames.size
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).readBytes().decodeToString()
        }

    private companion object {
        const val SETTING = "animator_duration_scale"
        const val SAMPLES = 6
        const val SAMPLE_INTERVAL_MS = 170L
    }
}
