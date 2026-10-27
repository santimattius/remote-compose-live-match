package example.livematch.android

import android.content.Context
import android.icu.text.DecimalFormat
import androidx.compose.remote.creation.compose.ExperimentalRemoteCreationComposeApi
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.action.valueChange
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.defaultMinSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.graphicsLayer
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.stateDescription
import androidx.compose.remote.creation.compose.state.RemoteConfiguration
import androidx.compose.remote.creation.compose.state.RemoteDp
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteTimeVariables
import androidx.compose.remote.creation.compose.state.rememberMutableRemoteFloat
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import example.livematch.contract.ActionId
import example.livematch.contract.FinishedMatch
import example.livematch.contract.LiveMatch
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.ScheduledMatch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Captures validated match data as a supported public Remote Compose component tree on Android. */
@OptIn(ExperimentalRemoteCreationComposeApi::class)
class AndroidMatchDocumentCompiler(
    private val context: Context,
    private val reduceMotion: () -> Boolean = { MotionPreference.isReduced(context) },
) {
    suspend fun compile(match: ScheduledMatch): RemoteDocumentPayload =
        captureSingleRemoteDocument(context) { RemoteScheduledMatchContent(match) }.toPayload()

    suspend fun compile(match: LiveMatch): RemoteDocumentPayload =
        captureSingleRemoteDocument(context) { RemoteLiveMatchContent(match, Instant.now().epochSecond.toInt(), reduceMotion()) }.toPayload()

    suspend fun compile(match: FinishedMatch): RemoteDocumentPayload =
        captureSingleRemoteDocument(context) { RemoteFinishedMatchContent(match) }.toPayload()

    private fun androidx.compose.remote.creation.compose.capture.CapturedDocument.toPayload(): RemoteDocumentPayload =
        RemoteDocumentPayload.from(bytes, bytes.size)
}

/**
 * Numeric ids baked into captured documents. They are assigned explicitly, not from enum order, so
 * reordering [ActionId] never changes what a cached document asks the host to do; adding an entry
 * fails to compile until it is given an id here.
 */
internal object HostActionIds {
    fun of(actionId: ActionId): Int =
        when (actionId) {
            ActionId.SET_REMINDER -> 1
            ActionId.OPEN_MATCH -> 2
            ActionId.OPEN_HIGHLIGHT -> 3
            ActionId.OPEN_SUMMARY -> 4
            ActionId.REFRESH -> 5
        }
}

/** Public, host-evaluated presentation values for [RemoteLiveMatchContent]. */
object LiveMatchRemotePresentation {
    const val detailsActionDescription = "Show local match details"

    fun startingClockSeconds(clock: String): Int {
        val parts = clock.split(":")
        require(parts.size == 2) { "Clock must use minutes:seconds" }
        val minutes = parts[0].toIntOrNull()
        val seconds = parts[1].toIntOrNull()
        require(minutes != null && minutes >= 0) { "Clock minutes must be a non-negative integer" }
        require(seconds != null && seconds in 0..59) { "Clock seconds must be between 00 and 59" }
        return minutes * 60 + seconds
    }

    fun accessibleMatchSummary(match: LiveMatch): String =
        "Live match. ${match.homeTeam} ${match.homeScore}, ${match.awayTeam} ${match.awayScore}. " +
            "${match.period} starting at ${match.clock}."
}

@Composable
@RemoteComposable
private fun RemoteScheduledMatchContent(match: ScheduledMatch) {
    val time = scheduledTime(match)
    RemoteColumn(modifier = matchContainer("Scheduled match. ${match.homeTeam} versus ${match.awayTeam}, $time.")) {
        RemoteText("UPCOMING".rs)
        RemoteText("${match.homeTeam} vs ${match.awayTeam}".rs)
        RemoteText(time.rs)
        RemoteText(
            text = "Remind me".rs,
            modifier = hostActionTarget(ActionId.SET_REMINDER, "Set reminder for ${match.homeTeam} versus ${match.awayTeam}"),
        )
    }
}

@Composable
@RemoteComposable
private fun RemoteLiveMatchContent(
    match: LiveMatch,
    captureEpochSecond: Int,
    reduceMotion: Boolean,
) {
    val startingClockSeconds = LiveMatchRemotePresentation.startingClockSeconds(match.clock)
    // The host evaluates the wall clock; anchoring to the capture instant makes the match clock start at
    // match.clock when the document is captured and advance locally from there, with no network updates.
    val matchClockSeconds = RemoteTimeVariables.epochSecond + (startingClockSeconds - captureEpochSecond)
    val clockMinutes = matchClockSeconds / 60
    val clockSeconds = matchClockSeconds.rem(60)
    // The pulse is decoration. The clock keeps advancing because it is information, not motion.
    val livePulseAlpha =
        if (reduceMotion) RemoteFloat(1f) else RemoteTimeVariables.continuousSeconds.rem(1f) * 0.6f + 0.4f
    val detailsVisible = rememberMutableRemoteFloat(0f)

    RemoteColumn(modifier = matchContainer(LiveMatchRemotePresentation.accessibleMatchSummary(match))) {
        RemoteText(
            text = "LIVE".rs,
            modifier =
                RemoteModifier
                    .graphicsLayer { alpha = livePulseAlpha }
                    .semantics {
                        contentDescription = "Live match status. The live indicator pulses locally.".rs
                    },
        )
        RemoteText("${match.homeTeam} ${match.homeScore}".rs)
        RemoteText("${match.awayTeam} ${match.awayScore}".rs)
        RemoteText(
            text =
                match.period.rs +
                    " ".rs +
                    clockMinutes.toRemoteString(DecimalFormat("0")) +
                    ":".rs +
                    clockSeconds.toRemoteString(DecimalFormat("00")),
            modifier =
                RemoteModifier.semantics {
                    contentDescription = "Match clock advances locally from ${match.clock}.".rs
                },
        )
        RemoteText(
            text = LiveMatchRemotePresentation.detailsActionDescription.rs,
            modifier =
                RemoteModifier
                    .defaultMinSize(
                        minWidth = RemoteDp(RemoteFloat(48f)),
                        minHeight = RemoteDp(RemoteFloat(48f)),
                    ).clickable(valueChange(detailsVisible, RemoteFloat(1f)))
                    .semantics {
                        contentDescription = LiveMatchRemotePresentation.detailsActionDescription.rs
                        stateDescription = "Activates local match details".rs
                    },
        )
        RemoteText(
            text = "Match ID: ${match.id}".rs,
            modifier =
                RemoteModifier
                    .graphicsLayer { alpha = detailsVisible }
                    .semantics {
                        contentDescription = "Local match details for ${match.id}".rs
                    },
        )
        RemoteText(
            text = "Open match".rs,
            modifier = hostActionTarget(ActionId.OPEN_MATCH, "Open live match ${match.id}"),
        )
        RemoteText(
            text = "View highlight".rs,
            modifier = hostActionTarget(ActionId.OPEN_HIGHLIGHT, "View highlight for ${match.id}"),
        )
    }
}

@Composable
@RemoteComposable
private fun RemoteFinishedMatchContent(match: FinishedMatch) {
    RemoteColumn(
        modifier =
            matchContainer(
                "Finished match. ${match.homeTeam} ${match.finalHomeScore}, " +
                    "${match.awayTeam} ${match.finalAwayScore}. ${match.statusLabel}.",
            ),
    ) {
        RemoteText(match.statusLabel.rs)
        RemoteText("${match.homeTeam} ${match.finalHomeScore}".rs)
        RemoteText("${match.awayTeam} ${match.finalAwayScore}".rs)
        RemoteText(
            text = "View summary".rs,
            modifier = hostActionTarget(ActionId.OPEN_SUMMARY, "Open summary for ${match.id}"),
        )
    }
}

private fun matchContainer(description: String): RemoteModifier =
    RemoteModifier
        .fillMaxWidth()
        .padding(RemoteConfiguration.windowWidth / 24f)
        .semantics {
            contentDescription = description.rs
        }

private fun hostActionTarget(
    actionId: ActionId,
    description: String,
): RemoteModifier =
    RemoteModifier
        .defaultMinSize(
            minWidth = RemoteDp(RemoteFloat(48f)),
            minHeight = RemoteDp(RemoteFloat(48f)),
        // The id-based overload is delivered to the player's IdActionCallbacks; the String-only
        // overload creates a named action that the host listener never receives.
        ).clickable(hostAction(HostActionIds.of(actionId), actionId.wireValue.rs))
        .semantics {
            contentDescription = description.rs
        }

private fun scheduledTime(match: ScheduledMatch): String =
    DateTimeFormatter
        .ofPattern("MMM d, HH:mm 'UTC'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochMilli(match.scheduledEpochMillis))
