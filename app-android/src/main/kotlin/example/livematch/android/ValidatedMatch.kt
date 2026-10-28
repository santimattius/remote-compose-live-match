package example.livematch.android

import example.livematch.contract.RemoteDocumentPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * What the signed envelope says about a match, kept next to its payload so the cache can tell an older document
 * from a newer one. Every field is covered by the envelope signature, so none of them can be forged.
 */
data class SnapshotMetadata(
    val matchId: String,
    val revision: Long,
    val createdAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
) {
    init {
        require(matchId.isNotBlank()) { "matchId must not be blank." }
    }
}

/** A match payload that passed the trust boundary, with the metadata of the envelope that carried it. */
data class ValidatedMatch(
    val metadata: SnapshotMetadata,
    val document: RemoteDocumentPayload,
)

/**
 * Binary format of the cache file: a magic number, the metadata and the payload. A file that does not start with the
 * magic number (for example one written by an earlier version that stored only the payload) cannot be ordered or
 * aged, so it decodes to null and is treated as an empty cache.
 */
object SnapshotCodec {
    private const val MAGIC = 0x4C4B4731 // LKG1

    fun encode(match: ValidatedMatch): ByteArray {
        val payload = match.document.bytes
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeUTF(match.metadata.matchId)
                output.writeLong(match.metadata.revision)
                output.writeLong(match.metadata.createdAtEpochSeconds)
                output.writeLong(match.metadata.expiresAtEpochSeconds)
                output.writeInt(payload.size)
                output.write(payload)
            }
            bytes.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): ValidatedMatch? =
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC) return null
                val metadata = SnapshotMetadata(input.readUTF(), input.readLong(), input.readLong(), input.readLong())
                val length = input.readInt()
                // The payload must be exactly what is left, so truncated and extended files are both rejected.
                if (length < 0 || length != input.available()) return null
                val payload = ByteArray(length).also(input::readFully)
                ValidatedMatch(metadata, RemoteDocumentPayload.from(payload, payload.size))
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
