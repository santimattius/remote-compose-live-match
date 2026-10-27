package example.livematch.android

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import example.livematch.contract.ActionId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityActionDispatchTest {
    @Test
    fun tappingTheRemindMeTargetDispatchesSetReminderThroughTheAllowlist() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            RemoteNodeTapper.tapText("Remind me")

            scenario.onActivity { activity ->
                assertEquals(ActionId.SET_REMINDER, activity.lastDispatchedAction)
                assertEquals(HostDestination.Reminder("scheduled-2027-01"), activity.lastDestination)
            }
        }
    }
}
