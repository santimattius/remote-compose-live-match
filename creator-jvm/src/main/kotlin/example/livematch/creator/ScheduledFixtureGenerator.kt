package example.livematch.creator

import example.livematch.contract.ScheduledMatch
import example.livematch.contract.ScheduledMatchDocument
import example.livematch.contract.ScheduledMatchRequest
import example.livematch.contract.Theme
import example.livematch.contract.Viewport
import java.nio.file.Files
import java.nio.file.Path

object ScheduledFixtureGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args.firstOrNull() ?: "fixtures")
        val document = ProceduralJvmMatchDocumentCreator().create(request())
        validate(document)
        val files = mapOf("scheduled-match.rc" to document.payload.bytes, "scheduled-match.manifest" to manifest(document).toByteArray())
        if (args.contains("--check")) {
            files.forEach { (name, bytes) ->
                require(
                    Files.exists(root.resolve(name)) && Files.readAllBytes(root.resolve(name)).contentEquals(bytes),
                ) { "Stale fixture: $name" }
            }
        } else {
            Files.createDirectories(root)
            files.forEach { (name, bytes) -> Files.write(root.resolve(name), bytes) }
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

    private fun validate(document: ScheduledMatchDocument) {
        require(document.payload.bytes.isNotEmpty())
        require(document.envelope.verifies(document.payload))
    }

    private fun manifest(document: ScheduledMatchDocument) =
        with(document.metadata) {
            "creator=$creator\nversion=$version\nviewport=${viewport.widthPx}x${viewport.heightPx}\nlocale=$localeTag\ntheme=$theme\ngenerationEpochMillis=$generationEpochMillis\nsha256=${document.envelope.sha256}\nsigningBoundary=${document.envelope.signingBoundary}\n"
        }
}
