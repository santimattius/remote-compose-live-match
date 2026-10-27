package example.livematch.creator

import example.livematch.contract.CreatorMetadata
import example.livematch.contract.EnvelopeValidator
import example.livematch.contract.FinishedMatch
import example.livematch.contract.MatchPresentation
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

    fun fixture(): SignedFixturePublisher.Fixture =
        SignedFixturePublisher.Fixture(
            name = "finished-match",
            presentation =
                MatchPresentation.Finished(
                    FinishedMatch("finished-2027-01", "Orcas", "Tigers", 2, 1, "FINAL"),
                ),
            creatorMetadata =
                CreatorMetadata(
                    creator = "match-payload-finished",
                    version = "1",
                    viewport = Viewport(1080, 480),
                    localeTag = "en-US",
                    theme = Theme.DARK,
                    generationEpochMillis = 1_700_000_000_000,
                ),
            envelopeMetadata = envelopeMetadata(),
        )

    private fun envelopeMetadata() =
        EnvelopeMetadataInput(
            documentId = "finished-match",
            revision = 1L,
            matchId = "finished-2027-01",
            minHostVersion = EnvelopeValidator.CURRENT_HOST_VERSION,
            createdAtEpochSeconds = SignedFixturePublisher.FIXTURE_CREATED_AT_EPOCH_SECONDS,
            expiresAtEpochSeconds = SignedFixturePublisher.FIXTURE_EXPIRES_AT_EPOCH_SECONDS,
        )
}
