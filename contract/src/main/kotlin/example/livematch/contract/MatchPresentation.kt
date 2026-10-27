package example.livematch.contract

/**
 * Backend-owned match data accepted by the host before it captures a Remote Compose document.
 * This deliberately contains presentation facts, not Remote Compose instructions.
 */
sealed interface MatchPresentation {
    val matchId: String

    data class Scheduled(
        val match: ScheduledMatch,
    ) : MatchPresentation {
        override val matchId: String
            get() = match.id
    }

    data class Live(
        val match: LiveMatch,
    ) : MatchPresentation {
        override val matchId: String
            get() = match.id
    }

    data class Finished(
        val match: FinishedMatch,
    ) : MatchPresentation {
        override val matchId: String
            get() = match.id
    }
}
