package example.livematch.android

import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteDocumentPayload

/** Durable boundary for the single last-known-good Remote Compose document snapshot. */
interface LastKnownGoodDocumentStorage {
    /** Returns the last atomically persisted snapshot, or null when no snapshot exists. */
    fun read(): ByteArray?

    /** Atomically replaces the persisted snapshot with [documentBytes]. */
    fun write(documentBytes: ByteArray)
}

/** Validates an envelope candidate and exposes its match only when it passed the trust boundary. */
fun interface ValidatedDocumentLoader {
    fun load(
        candidate: ByteArray,
        nowEpochSeconds: Long,
    ): ValidatedMatch?
}

/**
 * Adapts the signed match-data trust boundary for use by [LastKnownGoodDocumentRepository].
 *
 * Validation remains centralized in [EnvelopeMatchLoader]; this adapter only exposes the validated match payload,
 * which is what the cache persists and the host later compiles, together with the metadata of its envelope.
 */
class EnvelopeValidatedDocumentLoader(
    private val envelopeMatchLoader: EnvelopeMatchLoader = EnvelopeMatchLoader(),
) : ValidatedDocumentLoader {
    override fun load(
        candidate: ByteArray,
        nowEpochSeconds: Long,
    ): ValidatedMatch? =
        when (val result = envelopeMatchLoader.load(candidate, nowEpochSeconds)) {
            is MatchEnvelopeLoadResult.Loaded ->
                ValidatedMatch(
                    SnapshotMetadata(
                        result.presentation.matchId,
                        result.revision,
                        result.createdAtEpochSeconds,
                        result.expiresAtEpochSeconds,
                    ),
                    MatchPresentationCodec.encode(result.presentation),
                )

            else -> null
        }
}

sealed interface DocumentResolution {
    /** A newly received envelope was validated and promoted to the durable snapshot. */
    data class Candidate(
        val match: ValidatedMatch,
    ) : DocumentResolution

    /** No candidate was available or acceptable, so playback should use the durable snapshot. */
    data class LastKnownGood(
        val match: ValidatedMatch,
    ) : DocumentResolution

    /** No usable candidate or durable snapshot exists; the host must render its local fallback. */
    data object LocalFallback : DocumentResolution
}

/**
 * Resolves a candidate to a safe match without allowing rejected input to change the cache.
 *
 * The host owns persistence by providing [storage]. This core intentionally has no Android
 * framework dependency, so it can be exercised deterministically in JVM tests and wired to a
 * lifecycle-appropriate Android store later.
 */
class LastKnownGoodDocumentRepository(
    private val storage: LastKnownGoodDocumentStorage,
    private val validatedDocumentLoader: ValidatedDocumentLoader = EnvelopeValidatedDocumentLoader(),
) {
    fun resolve(
        candidate: ByteArray?,
        nowEpochSeconds: Long,
    ): DocumentResolution {
        val validated = validatedCandidate(candidate, nowEpochSeconds)
        if (validated != null && isAcceptable(validated)) {
            promote(validated)
            return DocumentResolution.Candidate(validated)
        }
        return lastKnownGood()
    }

    /** Validates [candidate] without touching the cache; null when it is absent or rejected. */
    fun validatedCandidate(
        candidate: ByteArray?,
        nowEpochSeconds: Long,
    ): ValidatedMatch? = candidate?.let { validatedDocumentLoader.load(it.copyOf(), nowEpochSeconds) }

    /**
     * False when accepting [candidate] would roll the cached match back. For the same match, a lower revision is a
     * replay of an older document, and the same revision with different content is a conflict; both are refused.
     * The same revision with identical content is a harmless re-delivery. A different match or an empty cache never
     * conflicts.
     */
    fun isAcceptable(candidate: ValidatedMatch): Boolean {
        val cached = cachedSnapshot() ?: return true
        if (cached.metadata.matchId != candidate.metadata.matchId) return true
        return when {
            candidate.metadata.revision > cached.metadata.revision -> true
            candidate.metadata.revision == cached.metadata.revision -> candidate.document.bytes.contentEquals(cached.document.bytes)
            else -> false
        }
    }

    /** Makes [match] the durable snapshot; callers promote only matches they could actually use. */
    fun promote(match: ValidatedMatch) {
        storage.write(SnapshotCodec.encode(match))
    }

    fun lastKnownGood(): DocumentResolution = cachedSnapshot()?.let(DocumentResolution::LastKnownGood) ?: DocumentResolution.LocalFallback

    private fun cachedSnapshot(): ValidatedMatch? = storage.read()?.let(SnapshotCodec::decode)
}
