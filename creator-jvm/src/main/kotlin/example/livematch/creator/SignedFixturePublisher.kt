package example.livematch.creator

import example.livematch.contract.CreatorMetadata
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.RemoteDocumentPayload
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.RemoteUiEnvelopeCodec
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PrivateKey

/** Shared publishing and verification boundary for deterministic, signed demo fixtures. */
object SignedFixturePublisher {
    const val FIXTURE_CREATED_AT_EPOCH_SECONDS: Long = 1_700_000_000L
    const val FIXTURE_EXPIRES_AT_EPOCH_SECONDS: Long = 4_102_444_800L
    const val FIXTURE_VALIDATION_EPOCH_SECONDS: Long = 1_800_000_000L

    data class Fixture(
        val name: String,
        val payload: RemoteDocumentPayload,
        val creatorMetadata: CreatorMetadata,
        val envelopeMetadata: EnvelopeMetadataInput,
    )

    fun publish(
        root: Path,
        fixture: Fixture,
        privateKey: PrivateKey,
    ) {
        val envelope = EnvelopeSigner.sign(fixture.payload, fixture.envelopeMetadata, privateKey)
        val encoded = RemoteUiEnvelopeCodec.encode(envelope)
        Files.createDirectories(root)
        Files.write(root.resolve("${fixture.name}.rc"), fixture.payload.bytes)
        Files.write(root.resolve("${fixture.name}.rce"), encoded)
        Files.write(root.resolve("${fixture.name}.manifest"), manifest(fixture, envelope, encoded).toByteArray())
    }

    /** Verifies a committed fixture with only the embedded public key; no private key is loaded. */
    fun check(
        root: Path,
        fixture: Fixture,
    ) {
        val rawPath = root.resolve("${fixture.name}.rc")
        val rcePath = root.resolve("${fixture.name}.rce")
        val manifestPath = root.resolve("${fixture.name}.manifest")
        require(Files.exists(rawPath)) { "Missing fixture: $rawPath" }
        require(Files.exists(rcePath)) { "Missing fixture: $rcePath" }
        require(Files.exists(manifestPath)) { "Missing fixture: $manifestPath" }

        require(Files.readAllBytes(rawPath).contentEquals(fixture.payload.bytes)) { "Stale fixture: ${fixture.name}.rc" }
        val encoded = Files.readAllBytes(rcePath)
        val envelope = RemoteUiEnvelopeCodec.decode(encoded)
        require(EnvelopeValidator.validate(envelope, FIXTURE_VALIDATION_EPOCH_SECONDS) == EnvelopeValidationResult.Valid) {
            "Invalid signed fixture: ${fixture.name}.rce"
        }
        requireMetadata(envelope, fixture.envelopeMetadata)
        require(envelope.bytes.contentEquals(fixture.payload.bytes)) { "Stale fixture: ${fixture.name}.rce" }
        require(Files.readAllBytes(manifestPath).contentEquals(manifest(fixture, envelope, encoded).toByteArray())) {
            "Stale fixture: ${fixture.name}.manifest"
        }
    }

    private fun requireMetadata(
        envelope: RemoteUiEnvelope,
        metadata: EnvelopeMetadataInput,
    ) {
        require(envelope.schemaVersion == metadata.schemaVersion) { "Unexpected schema version." }
        require(envelope.documentId == metadata.documentId) { "Unexpected document ID." }
        require(envelope.revision == metadata.revision) { "Unexpected revision." }
        require(envelope.matchId == metadata.matchId) { "Unexpected match ID." }
        require(envelope.minHostVersion == metadata.minHostVersion) { "Unexpected minimum host version." }
        require(envelope.createdAtEpochSeconds == metadata.createdAtEpochSeconds) { "Unexpected creation time." }
        require(envelope.expiresAtEpochSeconds == metadata.expiresAtEpochSeconds) { "Unexpected expiration time." }
    }

    private fun manifest(
        fixture: Fixture,
        envelope: RemoteUiEnvelope,
        encoded: ByteArray,
    ): String =
        with(fixture.creatorMetadata) {
            "creator=$creator\n" +
                "version=$version\n" +
                "viewport=${viewport.widthPx}x${viewport.heightPx}\n" +
                "locale=$localeTag\n" +
                "theme=$theme\n" +
                "generationEpochMillis=$generationEpochMillis\n" +
                "sha256=${sha256Hex(fixture.payload.bytes)}\n" +
                "signingBoundary=EXTERNAL_SIGNATURE_REQUIRED\n" +
                "envelopeFormat=rce-${RemoteUiEnvelopeCodec.VERSION}\n" +
                "documentId=${envelope.documentId}\n" +
                "revision=${envelope.revision}\n" +
                "matchId=${envelope.matchId}\n" +
                "minHostVersion=${envelope.minHostVersion}\n" +
                "createdAtEpochSeconds=${envelope.createdAtEpochSeconds}\n" +
                "expiresAtEpochSeconds=${envelope.expiresAtEpochSeconds}\n" +
                "rceSha256=${sha256Hex(encoded)}\n"
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
