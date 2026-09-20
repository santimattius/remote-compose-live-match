package example.livematch.creator

import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import example.livematch.contract.CreatorMetadata
import example.livematch.contract.PayloadEnvelope
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.ScheduledMatchDocument
import example.livematch.contract.ScheduledMatchDocumentCreator
import example.livematch.contract.ScheduledMatchRequest
import example.livematch.contract.SigningBoundary

class ProceduralJvmMatchDocumentCreator : ScheduledMatchDocumentCreator {
    override fun create(request: ScheduledMatchRequest): ScheduledMatchDocument {
        val writer =
            RemoteComposeWriter(
                request.viewport.widthPx,
                request.viewport.heightPx,
                title(request),
                JvmRcPlatformServices(),
            ).apply {
                getRcPaint().setColor(BACKGROUND_COLOR).setAntiAlias(true).commit()
                drawRect(0f, 0f, request.viewport.widthPx.toFloat(), request.viewport.heightPx.toFloat())
                getRcPaint().setColor(TEXT_COLOR).setTextSize(TEXT_SIZE).setAntiAlias(true).commit()
                drawTextAnchored(request.match.homeTeam, PADDING_X, 100f, -1f, 0f, 0)
                drawTextAnchored(request.match.awayTeam, PADDING_X, 210f, -1f, 0f, 0)
                drawTextAnchored(request.match.scheduledEpochMillis.toString(), PADDING_X, 320f, -1f, 0f, 0)
            }
        val payload =
            RemoteDocumentPayload.from(
                writer.encodeToByteArray(),
                writer.bufferSize(),
            )
        return ScheduledMatchDocument(
            payload = payload,
            envelope = PayloadEnvelope.forPayload(payload, SigningBoundary.EXTERNAL_SIGNATURE_REQUIRED),
            metadata = CreatorMetadata(CREATOR, VERSION, request.viewport, request.localeTag, request.theme, request.generationEpochMillis),
        )
    }

    private fun title(request: ScheduledMatchRequest) =
        listOf(
            request.match.id,
            request.match.homeTeam,
            request.match.awayTeam,
            request.match.scheduledEpochMillis,
            request.viewport.widthPx,
            request.viewport.heightPx,
            request.localeTag,
            request.theme,
            request.generationEpochMillis,
        ).joinToString("|")

    private companion object {
        const val CREATOR = "procedural-jvm-scheduled-match"
        const val VERSION = "1"
        const val BACKGROUND_COLOR = 0xFF002B5C.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val TEXT_SIZE = 48f
        const val PADDING_X = 48f
    }
}
