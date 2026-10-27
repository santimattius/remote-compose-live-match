package example.livematch.contract

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Stable, dependency-free binary wire format for signed match data.
 *
 * Header: four-byte [MAGIC], one-byte [VERSION], then a four-byte body length. The body begins
 * with a one-byte variant and contains fixed-width numbers and length-prefixed UTF-8 strings.
 */
object MatchPresentationCodec {
    const val MAGIC: Int = 0x4D415443 // MATC
    const val VERSION: Int = 1
    const val MAX_PAYLOAD_BYTES: Int = 4_096
    const val MAX_STRING_BYTES: Int = 128
    const val MAX_SCORE: Int = 999

    fun encode(presentation: MatchPresentation): RemoteDocumentPayload {
        val body =
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { output ->
                    when (presentation) {
                        is MatchPresentation.Scheduled -> {
                            output.writeByte(Variant.SCHEDULED.wireValue)
                            output.writeMatch(presentation.match.id, presentation.match.homeTeam, presentation.match.awayTeam)
                            output.writeLong(presentation.match.scheduledEpochMillis)
                        }

                        is MatchPresentation.Live -> {
                            output.writeByte(Variant.LIVE.wireValue)
                            output.writeMatch(presentation.match.id, presentation.match.homeTeam, presentation.match.awayTeam)
                            output.writeScore(presentation.match.homeScore)
                            output.writeScore(presentation.match.awayScore)
                            output.writeBoundedString(presentation.match.period)
                            output.writeBoundedString(presentation.match.clock)
                        }

                        is MatchPresentation.Finished -> {
                            output.writeByte(Variant.FINISHED.wireValue)
                            output.writeMatch(presentation.match.id, presentation.match.homeTeam, presentation.match.awayTeam)
                            output.writeScore(presentation.match.finalHomeScore)
                            output.writeScore(presentation.match.finalAwayScore)
                            output.writeBoundedString(presentation.match.statusLabel)
                        }
                    }
                }
                bytes.toByteArray()
            }
        require(body.size <= MAX_PAYLOAD_BYTES - HEADER_BYTES) { "Match payload exceeds $MAX_PAYLOAD_BYTES bytes." }
        val encoded =
            ByteArrayOutputStream(HEADER_BYTES + body.size).use { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(MAGIC)
                    output.writeByte(VERSION)
                    output.writeInt(body.size)
                    output.write(body)
                }
                bytes.toByteArray()
            }
        return RemoteDocumentPayload.from(encoded, encoded.size)
    }

    fun decode(payload: RemoteDocumentPayload): MatchPresentation = decode(payload.bytes)

    fun decode(bytes: ByteArray): MatchPresentation {
        if (bytes.size < MAGIC_BYTES || readInt(bytes, 0) != MAGIC) {
            reject(INVALID_MAGIC)
        }
        if (bytes.size < HEADER_BYTES) {
            reject(INVALID_LENGTH)
        }
        val version = bytes[MAGIC_BYTES].toInt() and 0xFF
        if (version != VERSION) {
            reject("Unsupported match payload version: $version.")
        }
        val declaredBodyLength = readInt(bytes, MAGIC_BYTES + VERSION_BYTES)
        if (
            declaredBodyLength < 0 ||
            declaredBodyLength != bytes.size - HEADER_BYTES ||
            bytes.size > MAX_PAYLOAD_BYTES
        ) {
            reject(INVALID_LENGTH)
        }

        val reader = Reader(bytes, HEADER_BYTES)
        val variant = Variant.from(reader.readByte())
        val matchId = reader.readString()
        val homeTeam = reader.readString()
        val awayTeam = reader.readString()
        val presentation =
            when (variant) {
                Variant.SCHEDULED -> {
                    MatchPresentation.Scheduled(
                        ScheduledMatch(matchId, homeTeam, awayTeam, reader.readLong()),
                    )
                }

                Variant.LIVE -> {
                    MatchPresentation.Live(
                        LiveMatch(
                            matchId,
                            homeTeam,
                            awayTeam,
                            reader.readScore(),
                            reader.readScore(),
                            reader.readString(),
                            reader.readString(),
                        ),
                    )
                }

                Variant.FINISHED -> {
                    MatchPresentation.Finished(
                        FinishedMatch(
                            matchId,
                            homeTeam,
                            awayTeam,
                            reader.readScore(),
                            reader.readScore(),
                            reader.readString(),
                        ),
                    )
                }
            }
        if (reader.remaining != 0) {
            reject(TRAILING_DATA)
        }
        return presentation
    }

    private fun DataOutputStream.writeMatch(
        id: String,
        homeTeam: String,
        awayTeam: String,
    ) {
        writeBoundedString(id)
        writeBoundedString(homeTeam)
        writeBoundedString(awayTeam)
    }

    private fun DataOutputStream.writeBoundedString(value: String) {
        require(value.isNotBlank()) { "Match payload string must not be blank." }
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "Match payload string exceeds $MAX_STRING_BYTES UTF-8 bytes." }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeScore(score: Int) {
        require(score in 0..MAX_SCORE) { "Match payload score out of range." }
        writeInt(score)
    }

    private fun readInt(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (bytes[offset].toInt() and 0xFF shl 24) or
            (bytes[offset + 1].toInt() and 0xFF shl 16) or
            (bytes[offset + 2].toInt() and 0xFF shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun reject(message: String): Nothing = throw IllegalArgumentException(message)

    private enum class Variant(
        val wireValue: Int,
    ) {
        SCHEDULED(1),
        LIVE(2),
        FINISHED(3),
        ;

        companion object {
            fun from(wireValue: Int): Variant =
                entries.firstOrNull { it.wireValue == wireValue }
                    ?: reject("Unknown match payload variant: $wireValue.")
        }
    }

    private class Reader(
        private val bytes: ByteArray,
        private var position: Int,
    ) {
        val remaining: Int
            get() = bytes.size - position

        fun readByte(): Int {
            requireRemaining(1)
            return bytes[position++].toInt() and 0xFF
        }

        fun readInt(): Int {
            requireRemaining(Int.SIZE_BYTES)
            val value = MatchPresentationCodec.readInt(bytes, position)
            position += Int.SIZE_BYTES
            return value
        }

        fun readLong(): Long {
            requireRemaining(Long.SIZE_BYTES)
            var value = 0L
            repeat(Long.SIZE_BYTES) {
                value = (value shl 8) or (bytes[position++].toLong() and 0xFF)
            }
            return value
        }

        fun readScore(): Int {
            val score = readInt()
            if (score !in 0..MAX_SCORE) {
                reject("Match payload score out of range.")
            }
            return score
        }

        fun readString(): String {
            val length = readInt()
            if (length !in 1..MAX_STRING_BYTES) {
                reject("Invalid match payload string length.")
            }
            requireRemaining(length)
            val stringBytes = bytes.copyOfRange(position, position + length)
            position += length
            return try {
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(stringBytes))
                    .toString()
                    .also { value ->
                        if (value.isBlank()) {
                            reject("Match payload string must not be blank.")
                        }
                    }
            } catch (_: CharacterCodingException) {
                reject("Invalid UTF-8 match payload.")
            }
        }

        private fun requireRemaining(length: Int) {
            if (length < 0 || length > remaining) {
                reject(INVALID_LENGTH)
            }
        }
    }

    private const val MAGIC_BYTES = Int.SIZE_BYTES
    private const val VERSION_BYTES = 1
    private const val HEADER_BYTES = MAGIC_BYTES + VERSION_BYTES + Int.SIZE_BYTES
    private const val INVALID_MAGIC = "Invalid match payload magic."
    private const val INVALID_LENGTH = "Invalid match payload length."
    private const val TRAILING_DATA = "Trailing data in match payload."
}
