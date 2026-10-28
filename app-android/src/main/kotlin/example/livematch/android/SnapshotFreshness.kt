package example.livematch.android

import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec

enum class Freshness {
    /** The cached match may be shown as current. */
    FRESH,

    /** The envelope that carried the match has expired. */
    EXPIRED,

    /** The match is still valid but no longer describes the present, so it must not be shown as current. */
    STALE,
}

/**
 * Decides whether a match read back from the cache may still be shown as current. A fresh candidate is judged by its
 * envelope expiry alone, because the backend vouches for it; a cached one is also judged by what it shows, since a
 * live score or an upcoming kickoff stops being true while the app is offline.
 */
object SnapshotFreshness {
    /** How long after creation a live score may still be shown as current. */
    const val LIVE_MAX_AGE_SECONDS = 15 * 60L

    fun of(
        match: ValidatedMatch,
        nowEpochSeconds: Long,
    ): Freshness {
        if (nowEpochSeconds >= match.metadata.expiresAtEpochSeconds) return Freshness.EXPIRED
        val stale =
            when (val presentation = MatchPresentationCodec.decode(match.document)) {
                is MatchPresentation.Live -> nowEpochSeconds - match.metadata.createdAtEpochSeconds > LIVE_MAX_AGE_SECONDS
                is MatchPresentation.Scheduled -> nowEpochSeconds * MILLIS_PER_SECOND >= presentation.match.scheduledEpochMillis
                is MatchPresentation.Finished -> false
            }
        return if (stale) Freshness.STALE else Freshness.FRESH
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
