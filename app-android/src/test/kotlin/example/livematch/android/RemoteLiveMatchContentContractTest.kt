package example.livematch.android

import example.livematch.contract.LiveMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteLiveMatchContentContractTest {
    @Test
    fun `derives host clock starting total from supplied match clock`() {
        assertEquals(4_032, LiveMatchRemotePresentation.startingClockSeconds("67:12"))
        assertEquals(12, LiveMatchRemotePresentation.startingClockSeconds("00:12"))
    }

    @Test
    fun `rejects malformed supplied match clocks`() {
        assertFailsWith<IllegalArgumentException> {
            LiveMatchRemotePresentation.startingClockSeconds("67:60")
        }
        assertFailsWith<IllegalArgumentException> {
            LiveMatchRemotePresentation.startingClockSeconds("67")
        }
    }

    @Test
    fun `describes live score and local detail action for accessibility`() {
        val match =
            LiveMatch(
                id = "live-2027-01",
                homeTeam = "Orcas",
                awayTeam = "Tigers",
                homeScore = 2,
                awayScore = 1,
                period = "2nd Half",
                clock = "67:12",
            )

        assertEquals(
            "Live match. Orcas 2, Tigers 1. 2nd Half starting at 67:12.",
            LiveMatchRemotePresentation.accessibleMatchSummary(match),
        )
        assertEquals(
            "Show local match details",
            LiveMatchRemotePresentation.detailsActionDescription,
        )
    }
}
