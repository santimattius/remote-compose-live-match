package example.livematch.android

import example.livematch.contract.RemoteDocumentPayload
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LastKnownGoodDocumentRepositoryTest {
    @Test
    fun `promotes only bytes returned by the validated candidate loader`() {
        val validatedBytes = byteArrayOf(1, 2, 3)
        val storage = RecordingStorage()
        val repository =
            LastKnownGoodDocumentRepository(
                storage = storage,
                validatedDocumentLoader = { _, _ -> document(validatedBytes) },
            )

        val result = repository.resolve(candidate = byteArrayOf(9), nowEpochSeconds = NOW)

        val promoted = assertIs<DocumentResolution.Candidate>(result)
        assertContentEquals(validatedBytes, promoted.document.bytes)
        assertContentEquals(validatedBytes, storage.persistedBytes)
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun `rejected candidates return last known good bytes without mutating storage`() {
        val lastKnownGood = byteArrayOf(4, 5, 6)
        val storage = RecordingStorage(lastKnownGood)
        val repository =
            LastKnownGoodDocumentRepository(
                storage = storage,
                validatedDocumentLoader = { _, _ -> null },
            )

        val result = repository.resolve(candidate = byteArrayOf(9), nowEpochSeconds = NOW)

        val cached = assertIs<DocumentResolution.LastKnownGood>(result)
        assertContentEquals(lastKnownGood, cached.document.bytes)
        assertContentEquals(lastKnownGood, storage.persistedBytes)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `unavailable candidates return persisted last known good bytes`() {
        val lastKnownGood = byteArrayOf(4, 5, 6)
        val storage = RecordingStorage(lastKnownGood)
        val repository = LastKnownGoodDocumentRepository(storage)

        val result = repository.resolve(candidate = null, nowEpochSeconds = NOW)

        val cached = assertIs<DocumentResolution.LastKnownGood>(result)
        assertContentEquals(lastKnownGood, cached.document.bytes)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `unavailable candidate without persisted document returns local fallback`() {
        val storage = RecordingStorage()
        val repository = LastKnownGoodDocumentRepository(storage)

        val result = repository.resolve(candidate = null, nowEpochSeconds = NOW)

        assertEquals(DocumentResolution.LocalFallback, result)
        assertEquals(0, storage.writeCount)
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

        fun document(bytes: ByteArray): RemoteDocumentPayload = RemoteDocumentPayload.from(bytes, bytes.size)
    }
}
