package example.livematch.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionAllowlistTest {
    @Test
    fun setReminderIsOnlyAllowedWhenScheduled() {
        assertAllowedOnly(ActionId.SET_REMINDER, MatchPhase.SCHEDULED)
    }

    @Test
    fun openMatchIsOnlyAllowedWhenLive() {
        assertAllowedOnly(ActionId.OPEN_MATCH, MatchPhase.LIVE)
    }

    @Test
    fun openHighlightIsOnlyAllowedWhenLive() {
        assertAllowedOnly(ActionId.OPEN_HIGHLIGHT, MatchPhase.LIVE)
    }

    @Test
    fun openSummaryIsOnlyAllowedWhenFinished() {
        assertAllowedOnly(ActionId.OPEN_SUMMARY, MatchPhase.FINISHED)
    }

    @Test
    fun refreshIsOnlyAllowedWhenSuspended() {
        assertAllowedOnly(ActionId.REFRESH, MatchPhase.SUSPENDED)
    }

    @Test
    fun cancelledPhaseAllowsNoActions() {
        for (actionId in ActionId.entries) {
            assertEquals(false, ActionAllowlist.isAllowed(MatchPhase.CANCELLED, actionId))
        }
    }

    @Test
    fun unknownWireValueDoesNotResolveToAnActionId() {
        assertNull(ActionId.fromWireValue("delete_everything"))
    }

    @Test
    fun everyActionIdRoundTripsThroughItsWireValue() {
        for (actionId in ActionId.entries) {
            assertEquals(actionId, ActionId.fromWireValue(actionId.wireValue))
        }
    }

    private fun assertAllowedOnly(
        actionId: ActionId,
        allowedPhase: MatchPhase,
    ) {
        for (phase in MatchPhase.entries) {
            assertEquals(phase == allowedPhase, ActionAllowlist.isAllowed(phase, actionId), "phase=$phase actionId=$actionId")
        }
    }
}
