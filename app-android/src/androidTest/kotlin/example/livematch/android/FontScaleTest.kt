package example.livematch.android

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/**
 * The article asks for font scale support. The documents resolve text size on the host, so these tests change the
 * system font scale and the window width on a device and assert that the cards stay on screen, never overlap and
 * keep their touch targets, and that the text really grows.
 */
@RunWith(AndroidJUnit4::class)
class FontScaleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var previousScale = "null"

    @Before
    fun rememberFontScale() {
        previousScale = shell("settings get system font_scale").trim()
    }

    @After
    fun restoreDisplayAndFont() {
        shell("wm size reset")
        if (previousScale.isEmpty() || previousScale == "null") {
            shell("settings delete system font_scale")
        } else {
            shell("settings put system font_scale $previousScale")
        }
        Thread.sleep(SETTLE_MS)
    }

    @Test
    fun liveCardStaysUsableWhenTheFontIsLargeAndTheWindowNarrow() {
        for (width in WINDOWS) {
            val normal = measureLive(NORMAL_SCALE, width)
            val large = measureLive(LARGE_SCALE, width)

            val growth = large.height("LIVE").toFloat() / normal.height("LIVE")
            assertTrue("Width $width: text should grow with the font scale but grew only ${growth}x", growth >= MIN_GROWTH)
        }
    }

    @Test
    fun launcherCardStaysUsableWhenTheFontIsLargeAndTheWindowNarrow() {
        for (width in WINDOWS) {
            for (scale in listOf(NORMAL_SCALE, LARGE_SCALE)) {
                applyDisplay(scale, width)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val screen = screenOf(scenario)
                    val texts = RemoteNodeTapper.renderedTexts("Remind me")
                    assertCardIsUsable("launcher scale=$scale width=$width", texts, screen, listOf("Remind me"))
                }
            }
        }
    }

    private fun measureLive(
        scale: String,
        width: Int?,
    ): List<Pair<String, Rect>> {
        applyDisplay(scale, width)
        val context = instrumentation.targetContext
        val intent =
            Intent()
                .setComponent(ComponentName(context, "example.livematch.android.DiagnosticPlaybackActivity"))
                .putExtra(DiagnosticPlaybackActivity.MODE_EXTRA, DiagnosticPlaybackActivity.MODE_PRE_ATTACH)
                .putExtra(DiagnosticPlaybackActivity.FIXTURE_EXTRA, DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME)
        return ActivityScenario.launch<DiagnosticPlaybackActivity>(intent).use { scenario ->
            val screen = screenOf(scenario)
            val texts = RemoteNodeTapper.renderedTexts("LIVE")
            assertCardIsUsable("live scale=$scale width=$width", texts, screen, listOf("Open match", "View highlight"))
            texts
        }
    }

    private fun assertCardIsUsable(
        label: String,
        texts: List<Pair<String, Rect>>,
        screen: Screen,
        targets: List<String>,
    ) {
        for ((text, bounds) in texts) {
            assertTrue("$label: '$text' $bounds leaves the ${screen.width}x${screen.height} screen", bounds.within(screen))
        }
        texts.zipWithNext().forEach { (upper, lower) ->
            assertTrue("$label: '${upper.first}' overlaps '${lower.first}'", upper.second.bottom <= lower.second.top)
        }
        for (target in targets) {
            val bounds = texts.first { it.first == target }.second
            assertTrue(
                "$label: touch target '$target' is ${bounds.height()}px, below ${MIN_TARGET_DP}dp",
                bounds.height() >= MIN_TARGET_DP * screen.density - 1,
            )
        }
    }

    private fun applyDisplay(
        scale: String,
        width: Int?,
    ) {
        shell("settings put system font_scale $scale")
        if (width == null) shell("wm size reset") else shell("wm size ${width}x${width * ASPECT_NUM / ASPECT_DEN}")
        // Let the configuration change finish so the activity does not launch into a changing window.
        Thread.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    private fun screenOf(scenario: ActivityScenario<*>): Screen {
        var screen: Screen? = null
        scenario.onActivity {
            val metrics = it.resources.displayMetrics
            screen = Screen(metrics.widthPixels, metrics.heightPixels, metrics.density)
        }
        return checkNotNull(screen)
    }

    private fun List<Pair<String, Rect>>.height(text: String): Int = first { it.first == text }.second.height()

    private fun Rect.within(screen: Screen) = left >= 0 && top >= 0 && right <= screen.width && bottom <= screen.height

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).readBytes().decodeToString()
        }

    private data class Screen(
        val width: Int,
        val height: Int,
        val density: Float,
    )

    private companion object {
        const val NORMAL_SCALE = "1.0"
        const val LARGE_SCALE = "2.0"
        const val MIN_GROWTH = 1.8f
        const val MIN_TARGET_DP = 48
        const val SETTLE_MS = 1_000L
        const val ASPECT_NUM = 20
        const val ASPECT_DEN = 9

        /** null keeps the device's own width; 540 is a narrow window that forces long lines to wrap. */
        val WINDOWS = listOf<Int?>(null, 540)
    }
}
