package example.livematch.android

import example.livematch.contract.ActionAllowlist
import example.livematch.contract.ActionId
import example.livematch.contract.MatchPhase

sealed interface HostDestination {
    val matchId: String

    data class Reminder(
        override val matchId: String,
    ) : HostDestination

    data class Match(
        override val matchId: String,
    ) : HostDestination

    data class Highlight(
        override val matchId: String,
    ) : HostDestination

    data class Summary(
        override val matchId: String,
    ) : HostDestination

    data class Refresh(
        override val matchId: String,
    ) : HostDestination
}

fun interface HostNavigator {
    fun navigate(destination: HostDestination)
}

enum class HostActionDispatchResult { Handled, Rejected }

/** Converts an allowlisted document request into a host-owned navigation effect. */
class HostActionDispatcher(
    private val matchId: String,
    private val navigator: HostNavigator,
) {
    fun dispatch(
        phase: MatchPhase,
        wireValue: String?,
    ): HostActionDispatchResult {
        val actionId = wireValue?.let(ActionId::fromWireValue) ?: return HostActionDispatchResult.Rejected
        if (!ActionAllowlist.isAllowed(phase, actionId)) return HostActionDispatchResult.Rejected

        navigator.navigate(actionId.toDestination())
        return HostActionDispatchResult.Handled
    }

    private fun ActionId.toDestination(): HostDestination =
        when (this) {
            ActionId.SET_REMINDER -> HostDestination.Reminder(matchId)
            ActionId.OPEN_MATCH -> HostDestination.Match(matchId)
            ActionId.OPEN_HIGHLIGHT -> HostDestination.Highlight(matchId)
            ActionId.OPEN_SUMMARY -> HostDestination.Summary(matchId)
            ActionId.REFRESH -> HostDestination.Refresh(matchId)
        }
}
