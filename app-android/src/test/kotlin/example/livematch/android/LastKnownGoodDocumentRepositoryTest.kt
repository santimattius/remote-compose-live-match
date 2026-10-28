package example.livematch.android

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LastKnownGoodDocumentRepositoryTest {
    private val cached = validated(payloadOf(4, 5, 6), matchId = "match-1", revision = 5)

    @Test
    fun `promotes only what the validated candidate loader returned, with its metadata`() {
        val candidate = validated(payloadOf(1, 2, 3), revision = 6)
        val storage = RecordingStorage()
        val repository = LastKnownGoodDocumentRepository(storage, validatedDocumentLoader = { _, _ -> candidate })

        val result = repository.resolve(candidate = byteArrayOf(9), nowEpochSeconds = NOW)

        assertEquals(candidate, assertIs<DocumentResolution.Candidate>(result).match)
        val persisted = assertNotNull(SnapshotCodec.decode(assertNotNull(storage.persistedBytes)))
        assertEquals(candidate.metadata, persisted.metadata)
        assertContentEquals(candidate.document.bytes, persisted.document.bytes)
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun `rejected candidates return the last known good match without mutating storage`() {
        val storage = RecordingStorage(snapshotBytes(cached))
        val repository = LastKnownGoodDocumentRepository(storage, validatedDocumentLoader = { _, _ -> null })

        val result = repository.resolve(candidate = byteArrayOf(9), nowEpochSeconds = NOW)

        assertEquals(cached.metadata, assertIs<DocumentResolution.LastKnownGood>(result).match.metadata)
        assertContentEquals(snapshotBytes(cached), storage.persistedBytes)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `unavailable candidates return the persisted last known good match`() {
        val storage = RecordingStorage(snapshotBytes(cached))

        val result = LastKnownGoodDocumentRepository(storage).resolve(candidate = null, nowEpochSeconds = NOW)

        assertContentEquals(cached.document.bytes, assertIs<DocumentResolution.LastKnownGood>(result).match.document.bytes)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `unavailable candidate without a persisted match returns the local fallback`() {
        val storage = RecordingStorage()

        val result = LastKnownGoodDocumentRepository(storage).resolve(candidate = null, nowEpochSeconds = NOW)

        assertEquals(DocumentResolution.LocalFallback, result)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `a snapshot without metadata cannot be ordered or aged, so it is ignored`() {
        val storage = RecordingStorage(byteArrayOf(4, 5, 6))

        val result = LastKnownGoodDocumentRepository(storage).resolve(candidate = null, nowEpochSeconds = NOW)

        assertEquals(DocumentResolution.LocalFallback, result)
    }

    private class RecordingStorage(
        initialBytes: ByteArray? = null,
    ) : LastKnownGoodDocumentStorage {
        private var storedBytes = initialBytes?.copyOf()

        var writeCount = 0
            private set

        val persistedBytes: ByteArray?
            get() = storedBytes?.copyOf()

        override fun read(): ByteArray? = storedBytes?.copyOf()

        override fun write(documentBytes: ByteArray) {
            writeCount += 1
            storedBytes = documentBytes.copyOf()
        }
    }

    private companion object {
        const val NOW = 1_800_000_000L
    }
}
