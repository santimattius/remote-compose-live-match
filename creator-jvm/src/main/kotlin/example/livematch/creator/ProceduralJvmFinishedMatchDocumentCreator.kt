package example.livematch.creator

import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import example.livematch.contract.ActionId
import example.livematch.contract.CreatorMetadata
import example.livematch.contract.FinishedMatchDocument
import example.livematch.contract.FinishedMatchDocumentCreator
import example.livematch.contract.FinishedMatchRequest
import example.livematch.contract.PayloadEnvelope
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.SigningBoundary

class ProceduralJvmFinishedMatchDocumentCreator : FinishedMatchDocumentCreator {
    override fun create(request: FinishedMatchRequest): FinishedMatchDocument {
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
                drawTextAnchored(request.match.statusLabel, PADDING_X, 100f, -1f, 0f, 0)
                drawTextAnchored(homeLine(request), PADDING_X, 210f, -1f, 0f, 0)
                drawTextAnchored(awayLine(request), PADDING_X, 320f, -1f, 0f, 0)
                getRcPaint().setColor(BUTTON_COLOR).setAntiAlias(true).commit()
                drawRect(PADDING_X, BUTTON_TOP, BUTTON_RIGHT, BUTTON_BOTTOM)
                getRcPaint().setColor(TEXT_COLOR).setTextSize(TEXT_SIZE).setAntiAlias(true).commit()
                drawTextAnchored("Summary", PADDING_X + BUTTON_TEXT_INSET, BUTTON_TOP + BUTTON_TEXT_INSET * 2f, -1f, 0f, 0)
                addClickArea(
                    nextId(),
                    "Open summary",
                    PADDING_X,
                    BUTTON_TOP,
                    BUTTON_RIGHT,
                    BUTTON_BOTTOM,
                    ActionId.OPEN_SUMMARY.wireValue,
                )
            }
        val payload =
            RemoteDocumentPayload.from(
                writer.encodeToByteArray(),
                writer.bufferSize(),
            )
        return FinishedMatchDocument(
            payload = payload,
            envelope = PayloadEnvelope.forPayload(payload, SigningBoundary.EXTERNAL_SIGNATURE_REQUIRED),
            metadata = CreatorMetadata(CREATOR, VERSION, request.viewport, request.localeTag, request.theme, request.generationEpochMillis),
        )
    }

    private fun homeLine(request: FinishedMatchRequest) = "${request.match.homeTeam} ${request.match.finalHomeScore}"

    private fun awayLine(request: FinishedMatchRequest) = "${request.match.awayTeam} ${request.match.finalAwayScore}"

    private fun title(request: FinishedMatchRequest) =
        listOf(
            request.match.id,
            request.match.homeTeam,
            request.match.awayTeam,
            request.match.finalHomeScore,
            request.match.finalAwayScore,
            request.match.statusLabel,
            request.viewport.widthPx,
            request.viewport.heightPx,
            request.localeTag,
            request.theme,
            request.generationEpochMillis,
        ).joinToString("|")

    private companion object {
        const val CREATOR = "procedural-jvm-finished-match"
        const val VERSION = "1"
        const val BACKGROUND_COLOR = 0xFF002B5C.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val TEXT_SIZE = 48f
        const val PADDING_X = 48f
        const val BUTTON_COLOR = 0xFF3E8ED0.toInt()
        const val BUTTON_TOP = 380f
        const val BUTTON_BOTTOM = 460f
        const val BUTTON_RIGHT = 600f
        const val BUTTON_TEXT_INSET = 16f
    }
}
