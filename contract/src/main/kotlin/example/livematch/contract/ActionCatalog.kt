package example.livematch.contract

enum class MatchPhase { SCHEDULED, LIVE, FINISHED, SUSPENDED, CANCELLED }

enum class ActionId(val wireValue: String) {
    SET_REMINDER("set_reminder"),
    OPEN_MATCH("open_match"),
    OPEN_HIGHLIGHT("open_highlight"),
    OPEN_SUMMARY("open_summary"),
    REFRESH("refresh"),
    ;

    companion object {
        fun fromWireValue(wireValue: String): ActionId? = entries.firstOrNull { it.wireValue == wireValue }
    }
}

/** Per spec section 6.3: the document emits intent, the host validates state before acting. */
object ActionAllowlist {
    private val allowedByPhase: Map<MatchPhase, Set<ActionId>> =
        mapOf(
            MatchPhase.SCHEDULED to setOf(ActionId.SET_REMINDER),
            MatchPhase.LIVE to setOf(ActionId.OPEN_MATCH, ActionId.OPEN_HIGHLIGHT),
            MatchPhase.FINISHED to setOf(ActionId.OPEN_SUMMARY),
            MatchPhase.SUSPENDED to setOf(ActionId.REFRESH),
            MatchPhase.CANCELLED to emptySet(),
        )

    fun isAllowed(
        phase: MatchPhase,
        actionId: ActionId,
    ): Boolean = allowedByPhase.getValue(phase).contains(actionId)
}
