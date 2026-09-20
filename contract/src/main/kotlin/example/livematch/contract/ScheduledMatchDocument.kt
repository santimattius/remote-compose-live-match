package example.livematch.contract

data class ScheduledMatch(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val scheduledEpochMillis: Long,
) {
    init {
        require(id.isNotBlank() && homeTeam.isNotBlank() && awayTeam.isNotBlank())
    }
}

data class Viewport(
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx > 0 && heightPx > 0)
    }
}

enum class Theme { LIGHT, DARK }

data class ScheduledMatchRequest(
    val match: ScheduledMatch,
    val viewport: Viewport,
    val localeTag: String,
    val theme: Theme,
    val generationEpochMillis: Long,
) {
    init {
        require(localeTag.isNotBlank())
    }
}

interface ScheduledMatchDocumentCreator {
    fun create(request: ScheduledMatchRequest): ScheduledMatchDocument
}

data class ScheduledMatchDocument(
    val payload: RemoteDocumentPayload,
    val envelope: PayloadEnvelope,
    val metadata: CreatorMetadata,
)

data class CreatorMetadata(
    val creator: String,
    val version: String,
    val viewport: Viewport,
    val localeTag: String,
    val theme: Theme,
    val generationEpochMillis: Long,
)
