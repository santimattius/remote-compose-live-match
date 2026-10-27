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

/** Validates an envelope candidate and exposes its document only when it passed the trust boundary. */
fun interface ValidatedDocumentLoader {
    fun load(
        candidate: ByteArray,
        nowEpochSeconds: Long,
    ): RemoteDocumentPayload?
}

/**
 * Adapts the signed match-data trust boundary for use by [LastKnownGoodDocumentRepository].
 *
 * Validation remains centralized in [EnvelopeMatchLoader]; this adapter only exposes the
 * validated match payload bytes, which are what the cache persists and the host later compiles.
 */
class EnvelopeValidatedDocumentLoader(
    private val envelopeMatchLoader: EnvelopeMatchLoader = EnvelopeMatchLoader(),
) : ValidatedDocumentLoader {
    override fun load(
        candidate: ByteArray,
        nowEpochSeconds: Long,
    ): RemoteDocumentPayload? =
        when (val result = envelopeMatchLoader.load(candidate, nowEpochSeconds)) {
            is MatchEnvelopeLoadResult.Loaded -> MatchPresentationCodec.encode(result.presentation)
            else -> null
        }
}

sealed interface DocumentResolution {
    /** A newly received envelope was validated and promoted to the durable snapshot. */
    data class Candidate(
        val document: RemoteDocumentPayload,
    ) : DocumentResolution

    /** No candidate was available or valid, so playback should use the durable snapshot. */
    data class LastKnownGood(
        val document: RemoteDocumentPayload,
    ) : DocumentResolution

    /** No usable candidate or durable snapshot exists; the host must render its local fallback. */
    data object LocalFallback : DocumentResolution
}

/**
 * Resolves a candidate to a safe document without allowing rejected input to change the cache.
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
        val validatedDocument = validatedCandidate(candidate, nowEpochSeconds)
        if (validatedDocument != null) {
            promote(validatedDocument)
            return DocumentResolution.Candidate(validatedDocument)
        }
        return lastKnownGood()
    }

    /** Validates [candidate] without touching the cache; null when it is absent or rejected. */
    fun validatedCandidate(
        candidate: ByteArray?,
        nowEpochSeconds: Long,
    ): RemoteDocumentPayload? = candidate?.let { validatedDocumentLoader.load(it.copyOf(), nowEpochSeconds) }

    /** Makes [document] the durable snapshot; callers promote only documents they could actually use. */
    fun promote(document: RemoteDocumentPayload) {
        storage.write(document.bytes)
    }

    fun lastKnownGood(): DocumentResolution {
        val persistedBytes = storage.read() ?: return DocumentResolution.LocalFallback
        val document =
            try {
                RemoteDocumentPayload.from(persistedBytes, persistedBytes.size)
            } catch (_: IllegalArgumentException) {
                null
            }
        return document?.let(DocumentResolution::LastKnownGood) ?: DocumentResolution.LocalFallback
    }
}
