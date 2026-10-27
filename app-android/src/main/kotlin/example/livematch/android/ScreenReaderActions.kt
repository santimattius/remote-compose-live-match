package example.livematch.android

import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.core.view.ViewCompat
import example.livematch.contract.ActionId
import example.livematch.contract.MatchPhase
import example.livematch.contract.MatchPresentation

internal fun MatchPresentation.phase(): MatchPhase =
    when (this) {
        is MatchPresentation.Scheduled -> MatchPhase.SCHEDULED
        is MatchPresentation.Live -> MatchPhase.LIVE
        is MatchPresentation.Finished -> MatchPhase.FINISHED
    }

/**
 * The actions each card shows. TalkBack does not focus the nodes inside the player, so the host also offers these
 * as screen-reader actions on the player view itself.
 */
internal object MatchCardActions {
    fun of(presentation: MatchPresentation): List<ActionId> =
        when (presentation) {
            is MatchPresentation.Scheduled -> listOf(ActionId.SET_REMINDER)
            is MatchPresentation.Live -> listOf(ActionId.OPEN_MATCH, ActionId.OPEN_HIGHLIGHT)
            is MatchPresentation.Finished -> listOf(ActionId.OPEN_SUMMARY)
        }

    fun label(actionId: ActionId): String =
        when (actionId) {
            ActionId.SET_REMINDER -> "Set reminder"
            ActionId.OPEN_MATCH -> "Open match"
            ActionId.OPEN_HIGHLIGHT -> "View highlight"
            ActionId.OPEN_SUMMARY -> "View summary"
            ActionId.REFRESH -> "Refresh"
        }
}

/**
 * Offers [actions] as accessibility actions on this player. [onAction] must go through the same allowlisted
 * dispatcher as a tap on the document, so a screen reader can never do more than a touch can.
 */
internal fun RemoteComposePlayer.exposeScreenReaderActions(
    actions: List<ActionId>,
    onAction: (ActionId) -> Unit,
) {
    actions.forEach { actionId ->
        ViewCompat.addAccessibilityAction(this, MatchCardActions.label(actionId)) { _, _ ->
            onAction(actionId)
            true
        }
    }
}
