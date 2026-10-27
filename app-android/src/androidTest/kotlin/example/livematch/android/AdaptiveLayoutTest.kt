package example.livematch.android

import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun restoreDisplaySize() {
        shell("wm size reset")
        // Let the display change finish so the next test does not launch into a resizing window.
        Thread.sleep(DISPLAY_SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    @Test
    fun layoutAdaptsToThePortraitAndLandscapeHostWindow() {
        listOf(Size(720, 1280), Size(1080, 2400), Size(1600, 720)).forEach { size ->
            shell("wm size ${size.width}x${size.height}")

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var density = 1f
                scenario.onActivity { density = it.resources.displayMetrics.density }

                val title = RemoteNodeTapper.boundsOf("UPCOMING")
                val target = RemoteNodeTapper.boundsOf("Remind me")

                // Padding is a fraction of the host window width, so it scales with the window.
                val expectedPadding = size.width / PADDING_DIVISOR
                assertTrue(
                    "$size: content should start near $expectedPadding px but starts at ${title.left}",
                    abs(title.left - expectedPadding) <= PADDING_TOLERANCE_PX,
                )
                assertTrue("$size: '$TARGET' $target must stay inside the screen", target.within(size))
                assertTrue(
                    "$size: touch target $target should be at least ${MIN_TARGET_DP}dp",
                    target.height() >= MIN_TARGET_DP * density - 1 && target.width() >= MIN_TARGET_DP * density - 1,
                )
            }
        }
    }

    private fun Rect.within(size: Size) = left >= 0 && top >= 0 && right <= size.width && bottom <= size.height

    private fun shell(command: String) {
        // Drain the output so the command has finished before the test continues.
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).readBytes()
        }
    }

    private data class Size(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val TARGET = "Remind me"
        const val PADDING_DIVISOR = 24
        const val PADDING_TOLERANCE_PX = 4
        const val MIN_TARGET_DP = 48
        const val DISPLAY_SETTLE_MS = 1_000L
    }
}
