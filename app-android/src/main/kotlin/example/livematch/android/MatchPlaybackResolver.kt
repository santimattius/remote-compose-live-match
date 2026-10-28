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

    /** A cached match exists but is expired or too old to be shown as current. */
    data object Stale : MatchPlayback
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
            // When the cache cannot be read there is nothing to roll back, so the candidate is not blocked.
            if (attempt { repository.isAcceptable(validated) } == false) {
                onFailure(RejectedUpdate("Rejected an update for match ${validated.metadata.matchId}: revision ${validated.metadata.revision} would roll the cached match back"))
            } else {
                playable(validated.document)?.let { playback ->
                    // A cache that cannot be written must not stop a match we already built from playing.
                    attempt { repository.promote(validated) }
                    return playback
                }
            }
        }
        val cached = (attempt { repository.lastKnownGood() } as? DocumentResolution.LastKnownGood)?.match
            ?: return MatchPlayback.LocalFallback
        // A payload that cannot be decoded cannot be judged, so it is treated like a missing cache.
        val freshness = attempt { SnapshotFreshness.of(cached, nowEpochSeconds) } ?: return MatchPlayback.LocalFallback
        if (freshness != Freshness.FRESH) {
            onFailure(StaleSnapshot("The cached match ${cached.metadata.matchId} is ${freshness.name.lowercase()} and was not shown as current"))
            return MatchPlayback.Stale
        }
        return playable(cached.document) ?: MatchPlayback.LocalFallback
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

/** An update that was validated but refused because it would replace the cached match with an older one. */
class RejectedUpdate(
    message: String,
) : Exception(message)

/** A cached match that was not shown because it is expired or too old to describe the present. */
class StaleSnapshot(
    message: String,
) : Exception(message)
