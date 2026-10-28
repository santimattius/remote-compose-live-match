package example.livematch.android

import example.livematch.contract.FinishedMatch
import example.livematch.contract.LiveMatch
import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.ScheduledMatch
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotFreshnessTest {
    private val created = 1_800_000_000L
    private val kickoffSeconds = 1_800_003_600L

    private fun live(
        createdAt: Long = created,
        expiresAt: Long = created + DAY,
    ) = ValidatedMatch(
        SnapshotMetadata("m", 3, createdAt, expiresAt),
        MatchPresentationCodec.encode(MatchPresentation.Live(LiveMatch("m", "Orcas", "Tigers", 2, 1, "2nd Half", "67:12"))),
    )

    private fun scheduled() =
        ValidatedMatch(
            SnapshotMetadata("m", 1, created, created + DAY),
            MatchPresentationCodec.encode(MatchPresentation.Scheduled(ScheduledMatch("m", "Orcas", "Tigers", kickoffSeconds * 1000))),
        )

    private fun finished() =
        ValidatedMatch(
            SnapshotMetadata("m", 9, created, created + 10 * DAY),
            MatchPresentationCodec.encode(MatchPresentation.Finished(FinishedMatch("m", "Orcas", "Tigers", 2, 1, "FINAL"))),
        )

    @Test
    fun `a live match is fresh until it is older than the limit`() {
        assertEquals(Freshness.FRESH, SnapshotFreshness.of(live(), created + SnapshotFreshness.LIVE_MAX_AGE_SECONDS - 1))
        assertEquals(Freshness.FRESH, SnapshotFreshness.of(live(), created + SnapshotFreshness.LIVE_MAX_AGE_SECONDS))
        assertEquals(Freshness.STALE, SnapshotFreshness.of(live(), created + SnapshotFreshness.LIVE_MAX_AGE_SECONDS + 1))
    }

    @Test
    fun `a scheduled match is stale once kickoff has passed`() {
        assertEquals(Freshness.FRESH, SnapshotFreshness.of(scheduled(), kickoffSeconds - 1))
        assertEquals(Freshness.STALE, SnapshotFreshness.of(scheduled(), kickoffSeconds))
    }

    @Test
    fun `a finished match does not age before its envelope expires`() {
        assertEquals(Freshness.FRESH, SnapshotFreshness.of(finished(), created + 9 * DAY))
    }

    @Test
    fun `every match is expired once its envelope expires`() {
        listOf(live(expiresAt = created + 60), scheduled().copy(metadata = scheduled().metadata.copy(expiresAtEpochSeconds = created + 60)), finished().copy(metadata = finished().metadata.copy(expiresAtEpochSeconds = created + 60))).forEach {
            assertEquals(Freshness.EXPIRED, SnapshotFreshness.of(it, created + 60))
        }
        assertEquals(Freshness.FRESH, SnapshotFreshness.of(live(expiresAt = created + 60), created + 59))
    }

    private companion object {
        const val DAY = 86_400L
    }
}
