package example.livematch.android

import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteDocumentPayload
import kotlin.coroutines.cancellation.CancellationException

sealed interface MatchPlayback {
    /** Validated match data and the Remote Compose document captured from it. */
    class Playable(
        val presentation: MatchPresentation,
        val document: RemoteDocumentPayload,
    ) : MatchPlayback

    /** Nothing usable exists, so the host renders its own local content instead of a blank player. */
    data object LocalFallback : MatchPlayback
}

/**
 * Turns a signed candidate into something playable: validate it, capture it into a Remote Compose
 * document with [compile], and only then promote it to [repository]'s cache. A candidate that
 * fails to compile therefore never replaces the last known good match, and any failure degrades
 * to [MatchPlayback.LocalFallback] so the player is never left blank.
 */
class MatchPlaybackResolver(
    private val repository: LastKnownGoodDocumentRepository,
    private val compile: suspend (MatchPresentation) -> RemoteDocumentPayload,
    private val onFailure: (Throwable) -> Unit = {},
) {
    suspend fun resolve(
        candidate: ByteArray?,
        nowEpochSeconds: Long,
    ): MatchPlayback {
        repository.validatedCandidate(candidate, nowEpochSeconds)?.let { validated ->
            playable(validated)?.let { playback ->
                // A cache that cannot be written must not stop a match we already built from playing.
                attempt { repository.promote(validated) }
                return playback
            }
        }
        val lastKnownGood = attempt { repository.lastKnownGood() } as? DocumentResolution.LastKnownGood
        return lastKnownGood?.let { playable(it.document) } ?: MatchPlayback.LocalFallback
    }

    private suspend fun playable(matchData: RemoteDocumentPayload): MatchPlayback.Playable? =
        attempt {
            val presentation = MatchPresentationCodec.decode(matchData)
            MatchPlayback.Playable(presentation, compile(presentation))
        }

    /** Runs [block], reporting and swallowing any failure other than cancellation. */
    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            onFailure(failure)
            null
        }
}
