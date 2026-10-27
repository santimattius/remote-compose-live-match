package example.livematch.creator

import example.livematch.contract.CreatorMetadata
import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.MatchPresentation
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteUiEnvelope
import example.livematch.contract.RemoteUiEnvelopeCodec
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PrivateKey

/** Shared publishing and verification boundary for deterministic, signed match-data fixtures. */
object SignedFixturePublisher {
    const val FIXTURE_CREATED_AT_EPOCH_SECONDS: Long = 1_700_000_000L
    const val FIXTURE_EXPIRES_AT_EPOCH_SECONDS: Long = 4_102_444_800L
    const val FIXTURE_VALIDATION_EPOCH_SECONDS: Long = 1_800_000_000L
    private const val PAYLOAD_EXTENSION = "match"

    data class Fixture(
        val name: String,
        val presentation: MatchPresentation,
        val creatorMetadata: CreatorMetadata,
        val envelopeMetadata: EnvelopeMetadataInput,
    ) {
        init {
            require(presentation.matchId == envelopeMetadata.matchId) { "Fixture match ID must match envelope metadata." }
        }

        val payload
            get() = MatchPresentationCodec.encode(presentation)
    }

    fun publish(
        root: Path,
        fixture: Fixture,
        privateKey: PrivateKey,
    ) {
        val payload = fixture.payload
        val envelope = EnvelopeSigner.sign(payload, fixture.envelopeMetadata, privateKey)
        val encoded = RemoteUiEnvelopeCodec.encode(envelope)
        Files.createDirectories(root)
        Files.write(root.resolve("${fixture.name}.$PAYLOAD_EXTENSION"), payload.bytes)
        Files.write(root.resolve("${fixture.name}.rce"), encoded)
        Files.write(root.resolve("${fixture.name}.manifest"), manifest(fixture, payload.bytes, envelope, encoded).toByteArray())
    }

    /** Verifies committed fixtures with only the embedded public key; no private key is loaded. */
    fun check(
        root: Path,
        fixture: Fixture,
    ) {
        val payload = fixture.payload
        val rawPath = root.resolve("${fixture.name}.$PAYLOAD_EXTENSION")
        val rcePath = root.resolve("${fixture.name}.rce")
        val manifestPath = root.resolve("${fixture.name}.manifest")
        require(Files.exists(rawPath)) { "Missing fixture: $rawPath" }
        require(Files.exists(rcePath)) { "Missing fixture: $rcePath" }
        require(Files.exists(manifestPath)) { "Missing fixture: $manifestPath" }

        require(Files.readAllBytes(rawPath).contentEquals(payload.bytes)) { "Stale fixture: ${fixture.name}.$PAYLOAD_EXTENSION" }
        val encoded = Files.readAllBytes(rcePath)
        val envelope = RemoteUiEnvelopeCodec.decode(encoded)
        require(EnvelopeValidator.validate(envelope, FIXTURE_VALIDATION_EPOCH_SECONDS) == EnvelopeValidationResult.Valid) {
            "Invalid signed fixture: ${fixture.name}.rce"
        }
        requireMetadata(envelope, fixture.envelopeMetadata)
        require(envelope.bytes.contentEquals(payload.bytes)) { "Stale fixture: ${fixture.name}.rce" }
        require(MatchPresentationCodec.decode(envelope.bytes) == fixture.presentation) { "Stale fixture: ${fixture.name}.rce" }
        require(Files.readAllBytes(manifestPath).contentEquals(manifest(fixture, payload.bytes, envelope, encoded).toByteArray())) {
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
        payload: ByteArray,
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
                "payloadFile=${fixture.name}.$PAYLOAD_EXTENSION\n" +
                "payloadFormat=match-payload-${MatchPresentationCodec.VERSION}\n" +
                "payloadSha256=${sha256Hex(payload)}\n" +
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
