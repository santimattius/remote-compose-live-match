package example.livematch.creator

import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.LiveMatch
import example.livematch.contract.LiveMatchDocument
import example.livematch.contract.LiveMatchRequest
import example.livematch.contract.Theme
import example.livematch.contract.Viewport
import java.nio.file.Path

object LiveFixtureGenerator {
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
        LiveMatchRequest(
            LiveMatch("live-2027-01", "Orcas", "Tigers", 2, 1, "2nd Half", "67:12"),
            Viewport(1080, 600),
            "en-US",
            Theme.DARK,
            1_700_000_000_000,
        )

    fun fixture(): SignedFixturePublisher.Fixture {
        val document = ProceduralJvmLiveMatchDocumentCreator().create(request())
        require(document.payload.bytes.isNotEmpty())
        require(document.envelope.verifies(document.payload))
        return SignedFixturePublisher.Fixture(
            name = "live-match",
            payload = document.payload,
            creatorMetadata = document.metadata,
            envelopeMetadata =
                EnvelopeMetadataInput(
                    documentId = "live-match",
                    revision = 1L,
                    matchId = "live-2027-01",
                    minHostVersion = EnvelopeValidator.CURRENT_HOST_VERSION,
                    createdAtEpochSeconds = SignedFixturePublisher.FIXTURE_CREATED_AT_EPOCH_SECONDS,
                    expiresAtEpochSeconds = SignedFixturePublisher.FIXTURE_EXPIRES_AT_EPOCH_SECONDS,
                ),
        )
    }
}
