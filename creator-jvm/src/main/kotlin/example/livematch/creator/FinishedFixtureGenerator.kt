package example.livematch.creator

import example.livematch.contract.FinishedMatch
import example.livematch.contract.FinishedMatchDocument
import example.livematch.contract.FinishedMatchRequest
import example.livematch.contract.Theme
import example.livematch.contract.Viewport
import java.nio.file.Files
import java.nio.file.Path

object FinishedFixtureGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args.firstOrNull() ?: "fixtures")
        val document = ProceduralJvmFinishedMatchDocumentCreator().create(request())
        validate(document)
        val files = mapOf("finished-match.rc" to document.payload.bytes, "finished-match.manifest" to manifest(document).toByteArray())
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
        FinishedMatchRequest(
            FinishedMatch("finished-2027-01", "Orcas", "Tigers", 2, 1, "FINAL"),
            Viewport(1080, 480),
            "en-US",
            Theme.DARK,
            1_700_000_000_000,
        )

    private fun validate(document: FinishedMatchDocument) {
        require(document.payload.bytes.isNotEmpty())
        require(document.envelope.verifies(document.payload))
    }

    private fun manifest(document: FinishedMatchDocument) =
        with(document.metadata) {
            "creator=$creator\nversion=$version\nviewport=${viewport.widthPx}x${viewport.heightPx}\nlocale=$localeTag\ntheme=$theme\ngenerationEpochMillis=$generationEpochMillis\nsha256=${document.envelope.sha256}\nsigningBoundary=${document.envelope.signingBoundary}\n"
        }
}
