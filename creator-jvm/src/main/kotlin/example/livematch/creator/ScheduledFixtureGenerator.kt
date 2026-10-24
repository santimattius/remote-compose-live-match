package example.livematch.creator

import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.ScheduledMatch
import example.livematch.contract.ScheduledMatchDocument
import example.livematch.contract.ScheduledMatchRequest
import example.livematch.contract.Theme
import example.livematch.contract.Viewport
import java.nio.file.Path

object ScheduledFixtureGenerator {
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
        ScheduledMatchRequest(
            ScheduledMatch("scheduled-2027-01", "Orcas", "Tigers", 1_800_000_000_000),
            Viewport(1080, 480),
            "en-US",
            Theme.DARK,
            1_700_000_000_000,
        )

    fun fixture(): SignedFixturePublisher.Fixture {
        val document = ProceduralJvmMatchDocumentCreator().create(request())
        require(document.payload.bytes.isNotEmpty())
        require(document.envelope.verifies(document.payload))
        return SignedFixturePublisher.Fixture(
            name = "scheduled-match",
            payload = document.payload,
            creatorMetadata = document.metadata,
            envelopeMetadata =
                EnvelopeMetadataInput(
                    documentId = "scheduled-match",
                    revision = 1L,
                    matchId = "scheduled-2027-01",
                    minHostVersion = EnvelopeValidator.CURRENT_HOST_VERSION,
                    createdAtEpochSeconds = SignedFixturePublisher.FIXTURE_CREATED_AT_EPOCH_SECONDS,
                    expiresAtEpochSeconds = SignedFixturePublisher.FIXTURE_EXPIRES_AT_EPOCH_SECONDS,
                ),
        )
    }
}
