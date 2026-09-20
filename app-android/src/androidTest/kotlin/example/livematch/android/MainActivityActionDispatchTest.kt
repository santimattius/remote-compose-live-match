package example.livematch.android

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import example.livematch.contract.ActionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityActionDispatchTest {
    @Test
    fun tappingTheRemindMeClickAreaDispatchesSetReminderThroughTheAllowlist() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            var clickResult = false
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val clickArea = findClickAreaView(root)
                assertNotNull("Expected a real ClickAreaView materialized from addClickArea", clickArea)
                clickResult = clickArea!!.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertTrue("View.performClick() should report a listener was invoked", clickResult)
            scenario.onActivity { activity ->
                assertEquals(ActionId.SET_REMINDER, activity.lastDispatchedAction)
            }
        }
    }

    private fun findClickAreaView(view: View): View? {
        if (view.javaClass.simpleName == "ClickAreaView") return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findClickAreaView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
