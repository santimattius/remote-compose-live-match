package example.livematch.contract

data class FinishedMatch(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val finalHomeScore: Int,
    val finalAwayScore: Int,
    val statusLabel: String,
) {
    init {
        require(id.isNotBlank() && homeTeam.isNotBlank() && awayTeam.isNotBlank())
        require(finalHomeScore >= 0 && finalAwayScore >= 0)
        require(statusLabel.isNotBlank())
    }
}

data class FinishedMatchRequest(
    val match: FinishedMatch,
    val viewport: Viewport,
    val localeTag: String,
    val theme: Theme,
    val generationEpochMillis: Long,
) {
    init {
        require(localeTag.isNotBlank())
    }
}

interface FinishedMatchDocumentCreator {
    fun create(request: FinishedMatchRequest): FinishedMatchDocument
}

data class FinishedMatchDocument(
    val payload: RemoteDocumentPayload,
    val envelope: PayloadEnvelope,
    val metadata: CreatorMetadata,
)
