package example.livematch.android

import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.ScheduledMatch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MatchPlaybackResolverTest {
    private val presentation = MatchPresentation.Scheduled(ScheduledMatch("match-1", "Orcas", "Tigers", 1_800_000_000_000))
    private val matchPayload = MatchPresentationCodec.encode(presentation)
    private val compiled = RemoteDocumentPayload.from(byteArrayOf(7, 7), 2)

    @Test
    fun `compiles a validated candidate for playback`() =
        runBlocking {
            val resolver = resolver(storedBytes = null, validated = matchPayload)

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            val playable = assertIs<MatchPlayback.Playable>(playback)
            assertEquals(presentation, playable.presentation)
            assertEquals(compiled, playable.document)
        }

    @Test
    fun `falls back to the last known good match when the candidate is rejected`() =
        runBlocking {
            val resolver = resolver(storedBytes = matchPayload.bytes, validated = null)

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
        }

    @Test
    fun `uses the local fallback when nothing usable exists or compilation fails`() =
        runBlocking {
            val nothing = resolver(storedBytes = null, validated = null).resolve(null, nowEpochSeconds = 1_500)
            val corrupt = resolver(storedBytes = byteArrayOf(1, 2, 3), validated = null).resolve(null, nowEpochSeconds = 1_500)
            val failing =
                resolver(storedBytes = null, validated = matchPayload, compile = { error("capture failed") })
                    .resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(MatchPlayback.LocalFallback, nothing)
            assertEquals(MatchPlayback.LocalFallback, corrupt)
            assertEquals(MatchPlayback.LocalFallback, failing)
        }

    @Test
    fun `keeps the last known good match when a valid candidate fails to compile`() =
        runBlocking {
            val broken = MatchPresentationCodec.encode(MatchPresentation.Scheduled(ScheduledMatch("broken", "A", "B", 1)))
            val storage = InMemoryStorage(matchPayload.bytes)
            val resolver =
                resolver(
                    storage = storage,
                    validated = broken,
                    compile = { if (it.matchId == "broken") error("capture failed") else compiled },
                )

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
            assertContentEquals(matchPayload.bytes, storage.read())
        }

    @Test
    fun `promotes a candidate to the cache only after it compiles`() =
        runBlocking {
            val storage = InMemoryStorage(null)

            resolver(storage = storage, validated = matchPayload).resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertContentEquals(matchPayload.bytes, storage.read())
        }

    @Test
    fun `still plays a compiled candidate when the cache cannot be written`() =
        runBlocking {
            val storage = ThrowingStorage(failWrites = true)

            val playback = resolver(storage = storage, validated = matchPayload).resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
        }

    @Test
    fun `uses the local fallback when the cache cannot be read`() =
        runBlocking {
            val storage = ThrowingStorage(failReads = true)

            val playback = resolver(storage = storage, validated = null).resolve(null, nowEpochSeconds = 1_500)

            assertEquals(MatchPlayback.LocalFallback, playback)
        }

    @Test
    fun `reports why a candidate could not be played`() =
        runBlocking {
            val failures = mutableListOf<Throwable>()
            val resolver =
                resolver(
                    storage = InMemoryStorage(null),
                    validated = matchPayload,
                    compile = { error("capture failed") },
                    onFailure = { failures += it },
                )

            resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(listOf("capture failed"), failures.map { it.message })
        }

    private fun resolver(
        storedBytes: ByteArray?,
        validated: RemoteDocumentPayload?,
        compile: suspend (MatchPresentation) -> RemoteDocumentPayload = { compiled },
    ) = resolver(InMemoryStorage(storedBytes), validated, compile)

    private fun resolver(
        storage: LastKnownGoodDocumentStorage,
        validated: RemoteDocumentPayload?,
        compile: suspend (MatchPresentation) -> RemoteDocumentPayload = { compiled },
        onFailure: (Throwable) -> Unit = {},
    ) = MatchPlaybackResolver(
        repository =
            LastKnownGoodDocumentRepository(
                storage = storage,
                validatedDocumentLoader = ValidatedDocumentLoader { _, _ -> validated },
            ),
        compile = compile,
        onFailure = onFailure,
    )

    private class ThrowingStorage(
        private val failReads: Boolean = false,
        private val failWrites: Boolean = false,
    ) : LastKnownGoodDocumentStorage {
        override fun read(): ByteArray? = if (failReads) throw java.io.IOException("read failed") else null

        override fun write(documentBytes: ByteArray) {
            if (failWrites) throw java.io.IOException("disk full")
        }
    }

    private class InMemoryStorage(
        private var bytes: ByteArray?,
    ) : LastKnownGoodDocumentStorage {
        override fun read() = bytes

        override fun write(documentBytes: ByteArray) {
            bytes = documentBytes
        }
    }
}
