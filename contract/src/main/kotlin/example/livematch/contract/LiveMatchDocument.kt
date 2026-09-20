package example.livematch.contract

data class LiveMatch(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val period: String,
    val clock: String,
) {
    init {
        require(id.isNotBlank() && homeTeam.isNotBlank() && awayTeam.isNotBlank())
        require(homeScore >= 0 && awayScore >= 0)
        require(period.isNotBlank() && clock.isNotBlank())
    }
}

data class LiveMatchRequest(
    val match: LiveMatch,
    val viewport: Viewport,
    val localeTag: String,
    val theme: Theme,
    val generationEpochMillis: Long,
) {
    init {
        require(localeTag.isNotBlank())
    }
}

interface LiveMatchDocumentCreator {
    fun create(request: LiveMatchRequest): LiveMatchDocument
}

data class LiveMatchDocument(
    val payload: RemoteDocumentPayload,
    val envelope: PayloadEnvelope,
    val metadata: CreatorMetadata,
)
