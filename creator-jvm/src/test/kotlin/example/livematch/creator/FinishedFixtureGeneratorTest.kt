package example.livematch.creator

import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.RemoteUiEnvelopeCodec
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FinishedFixtureGeneratorTest {
    @Test
    fun `publishes a deterministic signed finished fixture with stable metadata and exact RC bytes`() {
        val root = Files.createTempDirectory("finished-signed-fixture")
        try {
            val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val fixture = FinishedFixtureGenerator.fixture()
            val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

            SignedFixturePublisher.publish(root, fixture, keyPair.private)
            val first = Files.readAllBytes(root.resolve("finished-match.rce"))
            SignedFixturePublisher.publish(root, fixture, keyPair.private)
            val second = Files.readAllBytes(root.resolve("finished-match.rce"))
            val envelope = RemoteUiEnvelopeCodec.decode(second)

            assertContentEquals(first, second)
            assertEquals(EnvelopeValidationResult.Valid, EnvelopeValidator.validate(envelope, 1_800_000_000L, publicKeyBase64 = publicKey))
            assertEquals("finished-match", envelope.documentId)
            assertEquals(1L, envelope.revision)
            assertEquals("finished-2027-01", envelope.matchId)
            assertEquals(1, envelope.minHostVersion)
            assertEquals(1_700_000_000L, envelope.createdAtEpochSeconds)
            assertEquals(4_102_444_800L, envelope.expiresAtEpochSeconds)
            assertContentEquals(fixture.payload.bytes, envelope.bytes)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
