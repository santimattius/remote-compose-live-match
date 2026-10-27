package example.livematch.creator

import example.livematch.contract.EnvelopeValidationResult
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.MatchPresentationCodec
import example.livematch.contract.RemoteUiEnvelopeCodec
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LiveFixtureGeneratorTest {
    @Test
    fun `publishes deterministic signed live match data with stable metadata`() {
        val root = Files.createTempDirectory("live-signed-fixture")
        try {
            val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val fixture = LiveFixtureGenerator.fixture()
            val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

            SignedFixturePublisher.publish(root, fixture, keyPair.private)
            val first = Files.readAllBytes(root.resolve("live-match.rce"))
            SignedFixturePublisher.publish(root, fixture, keyPair.private)
            val second = Files.readAllBytes(root.resolve("live-match.rce"))
            val envelope = RemoteUiEnvelopeCodec.decode(second)

            assertContentEquals(first, second)
            assertEquals(EnvelopeValidationResult.Valid, EnvelopeValidator.validate(envelope, 1_800_000_000L, publicKeyBase64 = publicKey))
            assertEquals(fixture.presentation, MatchPresentationCodec.decode(envelope.bytes))
            assertContentEquals(fixture.payload.bytes, envelope.bytes)
            assertContentEquals(fixture.payload.bytes, Files.readAllBytes(root.resolve("live-match.match")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
