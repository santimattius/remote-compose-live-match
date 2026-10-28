package example.livematch.android

import example.livematch.contract.LiveMatch
import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.ScheduledMatch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatchPlaybackResolverTest {
    private val presentation = MatchPresentation.Scheduled(ScheduledMatch("match-1", "Orcas", "Tigers", 1_800_000_000_000))
    private val matchPayload = MatchPresentationCodec.encode(presentation)
    private val newerPresentation = MatchPresentation.Scheduled(ScheduledMatch("match-1", "Lions", "Bears", 1_800_000_000_000))
    private val newerPayload = MatchPresentationCodec.encode(newerPresentation)
    private val compiled = RemoteDocumentPayload.from(byteArrayOf(7, 7), 2)

    @Test
    fun `compiles a validated candidate for playback`() =
        runBlocking {
            val resolver = resolver(storedBytes = null, candidate = validated(matchPayload))

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            val playable = assertIs<MatchPlayback.Playable>(playback)
            assertEquals(presentation, playable.presentation)
            assertEquals(compiled, playable.document)
        }

    @Test
    fun `falls back to the last known good match when the candidate is rejected`() =
        runBlocking {
            val resolver = resolver(storedBytes = snapshotBytes(validated(matchPayload)), candidate = null)

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
        }

    @Test
    fun `uses the local fallback when nothing usable exists or compilation fails`() =
        runBlocking {
            val nothing = resolver(storedBytes = null, candidate = null).resolve(null, nowEpochSeconds = 1_500)
            val corrupt = resolver(storedBytes = byteArrayOf(1, 2, 3), candidate = null).resolve(null, nowEpochSeconds = 1_500)
            val failing =
                resolver(storedBytes = null, candidate = validated(matchPayload), compile = { error("capture failed") })
                    .resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(MatchPlayback.LocalFallback, nothing)
            assertEquals(MatchPlayback.LocalFallback, corrupt)
            assertEquals(MatchPlayback.LocalFallback, failing)
        }

    @Test
    fun `keeps the last known good match when a valid candidate fails to compile`() =
        runBlocking {
            val broken = validated(MatchPresentationCodec.encode(MatchPresentation.Scheduled(ScheduledMatch("broken", "A", "B", 1))), matchId = "broken")
            val storage = InMemoryStorage(snapshotBytes(validated(matchPayload)))
            val resolver =
                resolver(
                    storage = storage,
                    candidate = broken,
                    compile = { if (it.matchId == "broken") error("capture failed") else compiled },
                )

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
            assertContentEquals(matchPayload.bytes, storedPayload(storage))
        }

    @Test
    fun `promotes a candidate to the cache only after it compiles`() =
        runBlocking {
            val storage = InMemoryStorage(null)

            resolver(storage = storage, candidate = validated(matchPayload)).resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertContentEquals(matchPayload.bytes, storedPayload(storage))
        }

    @Test
    fun `still plays a compiled candidate when the cache cannot be written`() =
        runBlocking {
            val storage = ThrowingStorage(failWrites = true)

            val playback = resolver(storage = storage, candidate = validated(matchPayload)).resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
        }

    @Test
    fun `uses the local fallback when the cache cannot be read`() =
        runBlocking {
            val storage = ThrowingStorage(failReads = true)

            val playback = resolver(storage = storage, candidate = null).resolve(null, nowEpochSeconds = 1_500)

            assertEquals(MatchPlayback.LocalFallback, playback)
        }

    @Test
    fun `reports why a candidate could not be played`() =
        runBlocking {
            val failures = mutableListOf<Throwable>()
            val resolver =
                resolver(
                    storage = InMemoryStorage(null),
                    candidate = validated(matchPayload),
                    compile = { error("capture failed") },
                    onFailure = { failures += it },
                )

            resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(listOf("capture failed"), failures.map { it.message })
        }

    @Test
    fun `an older update for the same match does not replace the cached match`() =
        runBlocking {
            val cached = snapshotBytes(validated(matchPayload, revision = 5))
            val storage = InMemoryStorage(cached)
            val failures = mutableListOf<Throwable>()
            val resolver = resolver(storage = storage, candidate = validated(newerPayload, revision = 4), onFailure = { failures += it })

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
            assertContentEquals(cached, storage.read())
            assertTrue(assertNotNull(failures.singleOrNull()) is RejectedUpdate)
        }

    @Test
    fun `a newer update replaces the cached match`() =
        runBlocking {
            val storage = InMemoryStorage(snapshotBytes(validated(matchPayload, revision = 5)))

            val playback =
                resolver(storage = storage, candidate = validated(newerPayload, revision = 6))
                    .resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(newerPresentation, assertIs<MatchPlayback.Playable>(playback).presentation)
            assertEquals(6, SnapshotCodec.decode(assertNotNull(storage.read()))?.metadata?.revision)
        }

    @Test
    fun `delivering the same revision again plays it without reporting a rejection`() =
        runBlocking {
            val failures = mutableListOf<Throwable>()
            val resolver =
                resolver(
                    storage = InMemoryStorage(snapshotBytes(validated(matchPayload, revision = 5))),
                    candidate = validated(matchPayload, revision = 5),
                    onFailure = { failures += it },
                )

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_500)

            assertEquals(presentation, assertIs<MatchPlayback.Playable>(playback).presentation)
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `an offline cached live match that has gone stale is not shown as current`() =
        runBlocking {
            val live = liveMatch(createdAt = 1_000)
            val failures = mutableListOf<Throwable>()
            val resolver = resolver(storage = InMemoryStorage(snapshotBytes(live)), candidate = null, onFailure = { failures += it })

            val playback = resolver.resolve(null, nowEpochSeconds = 1_000 + SnapshotFreshness.LIVE_MAX_AGE_SECONDS + 1)

            assertEquals(MatchPlayback.Stale, playback)
            assertTrue(assertNotNull(failures.singleOrNull()) is StaleSnapshot)
        }

    @Test
    fun `an offline cached live match within the limit still plays`() =
        runBlocking<Unit> {
            val resolver = resolver(storedBytes = snapshotBytes(liveMatch(createdAt = 1_000)), candidate = null)

            val playback = resolver.resolve(null, nowEpochSeconds = 1_000 + SnapshotFreshness.LIVE_MAX_AGE_SECONDS)

            assertIs<MatchPlayback.Playable>(playback)
        }

    @Test
    fun `a cached match past its envelope expiry is not shown`() =
        runBlocking {
            val resolver = resolver(storedBytes = snapshotBytes(liveMatch(createdAt = 1_000, expiresAt = 1_100)), candidate = null)

            assertEquals(MatchPlayback.Stale, resolver.resolve(null, nowEpochSeconds = 1_100))
        }

    @Test
    fun `a fresh candidate is judged by its envelope expiry, not by the live age limit`() =
        runBlocking<Unit> {
            val resolver = resolver(storedBytes = null, candidate = liveMatch(createdAt = 1_000))

            val playback = resolver.resolve(byteArrayOf(9), nowEpochSeconds = 1_000 + 10 * SnapshotFreshness.LIVE_MAX_AGE_SECONDS)

            assertIs<MatchPlayback.Playable>(playback)
        }

    @Test
    fun `a cached snapshot whose payload cannot be decoded uses the local fallback`() =
        runBlocking {
            val corrupt = validated(payloadOf(1, 2, 3), matchId = "match-1")
            val resolver = resolver(storedBytes = snapshotBytes(corrupt), candidate = null)

            assertEquals(MatchPlayback.LocalFallback, resolver.resolve(null, nowEpochSeconds = 1_500))
        }

    private fun liveMatch(
        createdAt: Long,
        expiresAt: Long = 2_000_000_000,
    ) = ValidatedMatch(
        SnapshotMetadata("live-1", 1, createdAt, expiresAt),
        MatchPresentationCodec.encode(MatchPresentation.Live(LiveMatch("live-1", "Orcas", "Tigers", 2, 1, "2nd Half", "67:12"))),
    )

    private fun storedPayload(storage: InMemoryStorage): ByteArray = assertNotNull(SnapshotCodec.decode(assertNotNull(storage.read()))).document.bytes

    private fun resolver(
        storedBytes: ByteArray?,
        candidate: ValidatedMatch?,
        compile: suspend (MatchPresentation) -> RemoteDocumentPayload = { compiled },
    ) = resolver(InMemoryStorage(storedBytes), candidate, compile)

    private fun resolver(
        storage: LastKnownGoodDocumentStorage,
        candidate: ValidatedMatch?,
        compile: suspend (MatchPresentation) -> RemoteDocumentPayload = { compiled },
        onFailure: (Throwable) -> Unit = {},
    ) = MatchPlaybackResolver(
        repository =
            LastKnownGoodDocumentRepository(
                storage = storage,
                validatedDocumentLoader = ValidatedDocumentLoader { _, _ -> candidate },
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
