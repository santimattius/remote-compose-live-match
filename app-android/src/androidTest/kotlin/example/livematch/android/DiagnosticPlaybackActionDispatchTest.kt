package example.livematch.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import example.livematch.contract.ActionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticPlaybackActionDispatchTest {
    @Test
    fun tappingBothLiveClickAreasDispatchesOpenMatchAndOpenHighlightThroughTheAllowlist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = diagnosticIntent(context, DiagnosticPlaybackActivity.LIVE_MATCH_FIXTURE_NAME)

        ActivityScenario.launch<DiagnosticPlaybackActivity>(intent).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val clickAreas = mutableListOf<View>()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                findClickAreaViews(root, clickAreas)
            }
            assertEquals("Expected two real ClickAreaViews (open_match, open_highlight)", 2, clickAreas.size)

            val dispatched = mutableSetOf<ActionId>()
            clickAreas.forEach { clickArea ->
                scenario.onActivity { clickArea.performClick() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val action = activity.lastDispatchedAction
                    assertNotNull("Expected a dispatched action after tapping a click area", action)
                    dispatched += requireNotNull(action)
                }
            }
            assertEquals(setOf(ActionId.OPEN_MATCH, ActionId.OPEN_HIGHLIGHT), dispatched)
        }
    }

    @Test
    fun tappingTheFinishedClickAreaDispatchesOpenSummaryThroughTheAllowlist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = diagnosticIntent(context, DiagnosticPlaybackActivity.FINISHED_MATCH_FIXTURE_NAME)

        ActivityScenario.launch<DiagnosticPlaybackActivity>(intent).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            var clickArea: View? = null
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                clickArea = findClickAreaView(root)
            }
            assertNotNull("Expected a real ClickAreaView materialized from addClickArea", clickArea)

            scenario.onActivity { clickArea!!.performClick() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                assertEquals(ActionId.OPEN_SUMMARY, activity.lastDispatchedAction)
            }
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

    private fun findClickAreaView(view: View): View? {
        if (view.javaClass.simpleName == "ClickAreaView") return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findClickAreaView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findClickAreaViews(
        view: View,
        into: MutableList<View>,
    ) {
        if (view.javaClass.simpleName == "ClickAreaView") into += view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findClickAreaViews(view.getChildAt(i), into)
            }
        }
    }
}
