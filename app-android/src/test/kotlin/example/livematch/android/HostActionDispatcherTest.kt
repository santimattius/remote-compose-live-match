package example.livematch.android

import example.livematch.contract.MatchPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class HostActionDispatcherTest {
    @Test
    fun `allowed actions navigate through the host-owned boundary`() {
        val navigator = RecordingNavigator()
        val dispatcher = HostActionDispatcher(matchId = "scheduled-2027-01", navigator = navigator)

        val result = dispatcher.dispatch(MatchPhase.SCHEDULED, "set_reminder")

        assertEquals(HostActionDispatchResult.Handled, result)
        assertEquals(listOf<HostDestination>(HostDestination.Reminder("scheduled-2027-01")), navigator.destinations)
    }

    @Test
    fun `unknown and phase-disallowed actions remain inert`() {
        val navigator = RecordingNavigator()
        val dispatcher = HostActionDispatcher(matchId = "scheduled-2027-01", navigator = navigator)

        val unknown = dispatcher.dispatch(MatchPhase.SCHEDULED, "delete_account")
        val disallowed = dispatcher.dispatch(MatchPhase.SCHEDULED, "open_summary")

        assertEquals(HostActionDispatchResult.Rejected, unknown)
        assertEquals(HostActionDispatchResult.Rejected, disallowed)
        assertEquals(emptyList(), navigator.destinations)
    }

    private class RecordingNavigator : HostNavigator {
        val destinations = mutableListOf<HostDestination>()

        override fun navigate(destination: HostDestination) {
            destinations += destination
        }
    }
}
