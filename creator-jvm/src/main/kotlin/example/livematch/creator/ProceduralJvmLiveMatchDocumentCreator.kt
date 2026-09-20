package example.livematch.creator

import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import example.livematch.contract.CreatorMetadata
import example.livematch.contract.LiveMatchDocument
import example.livematch.contract.LiveMatchDocumentCreator
import example.livematch.contract.LiveMatchRequest
import example.livematch.contract.PayloadEnvelope
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.SigningBoundary

class ProceduralJvmLiveMatchDocumentCreator : LiveMatchDocumentCreator {
    override fun create(request: LiveMatchRequest): LiveMatchDocument {
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
                drawTextAnchored(liveLabel(), PADDING_X, 90f, -1f, 0f, 0)
                drawTextAnchored(homeLine(request), PADDING_X, 190f, -1f, 0f, 0)
                drawTextAnchored(awayLine(request), PADDING_X, 290f, -1f, 0f, 0)
                drawTextAnchored(periodAndClock(request), PADDING_X, 390f, -1f, 0f, 0)
            }
        val payload =
            RemoteDocumentPayload.from(
                writer.encodeToByteArray(),
                writer.bufferSize(),
            )
        return LiveMatchDocument(
            payload = payload,
            envelope = PayloadEnvelope.forPayload(payload, SigningBoundary.EXTERNAL_SIGNATURE_REQUIRED),
            metadata = CreatorMetadata(CREATOR, VERSION, request.viewport, request.localeTag, request.theme, request.generationEpochMillis),
        )
    }

    private fun liveLabel() = "LIVE"

    private fun homeLine(request: LiveMatchRequest) = "${request.match.homeTeam} ${request.match.homeScore}"

    private fun awayLine(request: LiveMatchRequest) = "${request.match.awayTeam} ${request.match.awayScore}"

    private fun periodAndClock(request: LiveMatchRequest) = "${request.match.period} ${request.match.clock}"

    private fun title(request: LiveMatchRequest) =
        listOf(
            request.match.id,
            request.match.homeTeam,
            request.match.awayTeam,
            request.match.homeScore,
            request.match.awayScore,
            request.match.period,
            request.match.clock,
            request.viewport.widthPx,
            request.viewport.heightPx,
            request.localeTag,
            request.theme,
            request.generationEpochMillis,
        ).joinToString("|")

    private companion object {
        const val CREATOR = "procedural-jvm-live-match"
        const val VERSION = "1"
        const val BACKGROUND_COLOR = 0xFF002B5C.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val TEXT_SIZE = 48f
        const val PADDING_X = 48f
    }
}
