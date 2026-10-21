package example.livematch.contract

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Dependency-free, versioned binary framing for a [RemoteUiEnvelope].
 *
 * The framing is: magic, codec version, fixed-width numeric metadata, then bounded
 * length-prefixed UTF-8 fields, signature, and Remote Compose document bytes. Lengths are
 * validated against both their field limit and remaining input before any corresponding
 * allocation is made.
 */
object RemoteUiEnvelopeCodec {
    val MAGIC: ByteArray = byteArrayOf('R'.code.toByte(), 'C'.code.toByte(), 'E'.code.toByte(), '!'.code.toByte())
    const val VERSION: Int = 1
    const val MAX_STRING_FIELD_BYTES: Int = 8 * 1024
    const val MAX_SIGNATURE_BYTES: Int = 8 * 1024
    const val MAX_DOCUMENT_BYTES: Int = EnvelopeValidator.MAX_ENVELOPE_BYTES
    const val MAX_ENCODED_BYTES: Int = 1_041_024

    fun encode(envelope: RemoteUiEnvelope): ByteArray {
        val documentId = utf8(envelope.documentId, "documentId", MAX_STRING_FIELD_BYTES)
        val matchId = utf8(envelope.matchId, "matchId", MAX_STRING_FIELD_BYTES)
        val contentSha256 = utf8(envelope.contentSha256, "contentSha256", MAX_STRING_FIELD_BYTES)
        val signatureAlgorithm = utf8(envelope.signatureAlgorithm, "signatureAlgorithm", MAX_STRING_FIELD_BYTES)
        val signature = utf8(envelope.signature, "signature", MAX_SIGNATURE_BYTES)
        val document = envelope.bytes
        require(document.size <= MAX_DOCUMENT_BYTES) { "Document bytes exceed $MAX_DOCUMENT_BYTES bytes." }

        val encodedSize =
            MAGIC.size.toLong() +
                Int.SIZE_BYTES +
                Int.SIZE_BYTES +
                lengthPrefixedSize(documentId) +
                Long.SIZE_BYTES +
                lengthPrefixedSize(matchId) +
                Int.SIZE_BYTES +
                Long.SIZE_BYTES +
                Long.SIZE_BYTES +
                lengthPrefixedSize(contentSha256) +
                lengthPrefixedSize(signatureAlgorithm) +
                lengthPrefixedSize(signature) +
                lengthPrefixedSize(document)
        require(encodedSize <= MAX_ENCODED_BYTES) { "Encoded envelope exceeds $MAX_ENCODED_BYTES bytes." }

        return ByteArrayOutputStream(encodedSize.toInt()).use { output ->
            DataOutputStream(output).use {
                it.write(MAGIC)
                it.writeInt(VERSION)
                it.writeInt(envelope.schemaVersion)
                it.writeLengthPrefixed(documentId)
                it.writeLong(envelope.revision)
                it.writeLengthPrefixed(matchId)
                it.writeInt(envelope.minHostVersion)
                it.writeLong(envelope.createdAtEpochSeconds)
                it.writeLong(envelope.expiresAtEpochSeconds)
                it.writeLengthPrefixed(contentSha256)
                it.writeLengthPrefixed(signatureAlgorithm)
                it.writeLengthPrefixed(signature)
                it.writeLengthPrefixed(document)
            }
            output.toByteArray()
        }
    }

    fun decode(encoded: ByteArray): RemoteUiEnvelope {
        require(encoded.size <= MAX_ENCODED_BYTES) { "Encoded envelope exceeds $MAX_ENCODED_BYTES bytes." }
        return try {
            DataInputStream(ByteArrayInputStream(encoded)).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                require(magic.contentEquals(MAGIC)) { "Unknown RCE magic." }
                require(input.readInt() == VERSION) { "Unsupported RCE codec version." }

                val envelope =
                    RemoteUiEnvelope(
                        schemaVersion = input.readInt(),
                        documentId = readUtf8(input, "documentId", MAX_STRING_FIELD_BYTES),
                        revision = input.readLong(),
                        matchId = readUtf8(input, "matchId", MAX_STRING_FIELD_BYTES),
                        minHostVersion = input.readInt(),
                        createdAtEpochSeconds = input.readLong(),
                        expiresAtEpochSeconds = input.readLong(),
                        contentSha256 = readUtf8(input, "contentSha256", MAX_STRING_FIELD_BYTES),
                        signatureAlgorithm = readUtf8(input, "signatureAlgorithm", MAX_STRING_FIELD_BYTES),
                        signature = readUtf8(input, "signature", MAX_SIGNATURE_BYTES),
                        bytes = readBoundedBytes(input, MAX_DOCUMENT_BYTES, "document"),
                    )
                require(input.available() == 0) { "Unexpected trailing RCE bytes." }
                envelope
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Malformed RCE envelope.", error)
        }
    }

    private fun utf8(
        value: String,
        field: String,
        maximumLength: Int,
    ): ByteArray =
        value.toByteArray(Charsets.UTF_8).also {
            require(it.size <= maximumLength) { "$field exceeds $maximumLength bytes." }
        }

    private fun lengthPrefixedSize(bytes: ByteArray): Long = Int.SIZE_BYTES.toLong() + bytes.size

    private fun DataOutputStream.writeLengthPrefixed(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun readUtf8(
        input: DataInputStream,
        field: String,
        maximumLength: Int,
    ): String =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(readBoundedBytes(input, maximumLength, field)))
                .toString()
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("$field is not valid UTF-8.", error)
        }

    private fun readBoundedBytes(
        input: DataInputStream,
        maximumLength: Int,
        field: String,
    ): ByteArray {
        val length = input.readInt()
        require(length >= 0) { "$field length must not be negative." }
        require(length <= maximumLength) { "$field length exceeds $maximumLength bytes." }
        require(length <= input.available()) { "Truncated $field." }
        return ByteArray(length).also(input::readFully)
    }
}
