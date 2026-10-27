package example.livematch.android

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import example.livematch.contract.MatchPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Measures the host-side cost of the signed match-data pipeline and reports it to logcat under
 * [TAG]. It asserts only against absurd values (hangs), because thresholds depend on the device.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceMeasurementTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reportsValidationDecodeCaptureAndFirstFrameTimings() {
        val envelope = context.assets.open("scheduled-match.rce").use { it.readBytes() }
        val loader = EnvelopeMatchLoader()

        val validateAndDecode =
            measure("validate+decode envelope") {
                check(loader.load(envelope, Instant.now().epochSecond) is MatchEnvelopeLoadResult.Loaded)
            }

        val presentation =
            (loader.load(envelope, Instant.now().epochSecond) as MatchEnvelopeLoadResult.Loaded).presentation
                as MatchPresentation.Scheduled
        val capture =
            measure("capture document") {
                runBlocking { AndroidMatchDocumentCompiler(context).compile(presentation.match) }
            }

        val startedAt = SystemClock.elapsedRealtime()
        val firstFrame =
            ActivityScenario.launch(MainActivity::class.java).use {
                RemoteNodeTapper.boundsOf("UPCOMING")
                SystemClock.elapsedRealtime() - startedAt
            }
        Log.i(TAG, "first useful frame (launch -> content visible): ${firstFrame}ms")

        assertTrue("validate+decode took ${validateAndDecode.median}ms", validateAndDecode.median < SANITY_LIMIT_MS)
        assertTrue("capture took ${capture.median}ms", capture.median < SANITY_LIMIT_MS)
        assertTrue("first frame took ${firstFrame}ms", firstFrame in 1 until SANITY_LIMIT_MS)
    }

    private fun measure(
        label: String,
        block: () -> Unit,
    ): Timing {
        val cold = nanosOf(block)
        val warm = List(WARM_RUNS) { nanosOf(block) }.sorted()
        val timing = Timing(cold = cold / NANOS_PER_MS, median = warm[warm.size / 2] / NANOS_PER_MS, best = warm.first() / NANOS_PER_MS)
        Log.i(TAG, "$label: cold=${timing.cold}ms median=${timing.median}ms best=${timing.best}ms (warm runs=$WARM_RUNS)")
        return timing
    }

    private fun nanosOf(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private data class Timing(
        val cold: Long,
        val median: Long,
        val best: Long,
    )

    private companion object {
        const val TAG = "LiveMatchPerf"
        const val WARM_RUNS = 20
        const val NANOS_PER_MS = 1_000_000L
        const val SANITY_LIMIT_MS = 10_000L
    }
}
