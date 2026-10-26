package example.livematch.creator

import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.FinishedMatch
import example.livematch.contract.FinishedMatchDocument
import example.livematch.contract.FinishedMatchRequest
import example.livematch.contract.Theme
import example.livematch.contract.Viewport
import java.nio.file.Path

object FinishedFixtureGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args.firstOrNull() ?: "fixtures")
        val fixture = fixture()
        if (args.contains("--check")) {
            SignedFixturePublisher.check(root, fixture)
        } else {
            SignedFixturePublisher.publish(root, fixture, EnvelopeSigningKeyLoader.loadFromEnv())
        }
    }

    private fun request() =
        FinishedMatchRequest(
            FinishedMatch("finished-2027-01", "Orcas", "Tigers", 2, 1, "FINAL"),
            Viewport(1080, 480),
            "en-US",
            Theme.DARK,
            1_700_000_000_000,
        )

    fun fixture(): SignedFixturePublisher.Fixture {
        val document = ProceduralJvmFinishedMatchDocumentCreator().create(request())
        require(document.payload.bytes.isNotEmpty())
        require(document.envelope.verifies(document.payload))
        return SignedFixturePublisher.Fixture(
            name = "finished-match",
            payload = document.payload,
            creatorMetadata = document.metadata,
            envelopeMetadata =
                EnvelopeMetadataInput(
                    documentId = "finished-match",
                    revision = 1L,
                    matchId = "finished-2027-01",
                    minHostVersion = EnvelopeValidator.CURRENT_HOST_VERSION,
                    createdAtEpochSeconds = SignedFixturePublisher.FIXTURE_CREATED_AT_EPOCH_SECONDS,
                    expiresAtEpochSeconds = SignedFixturePublisher.FIXTURE_EXPIRES_AT_EPOCH_SECONDS,
                ),
        )
    }
}
