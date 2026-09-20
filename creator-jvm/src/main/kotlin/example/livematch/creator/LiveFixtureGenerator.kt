package example.livematch.creator

import example.livematch.contract.LiveMatch
import example.livematch.contract.LiveMatchDocument
import example.livematch.contract.LiveMatchRequest
import example.livematch.contract.Theme
import example.livematch.contract.Viewport
import java.nio.file.Files
import java.nio.file.Path

object LiveFixtureGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args.firstOrNull() ?: "fixtures")
        val document = ProceduralJvmLiveMatchDocumentCreator().create(request())
        validate(document)
        val files = mapOf("live-match.rc" to document.payload.bytes, "live-match.manifest" to manifest(document).toByteArray())
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
        LiveMatchRequest(
            LiveMatch("live-2027-01", "Orcas", "Tigers", 2, 1, "2nd Half", "67:12"),
            Viewport(1080, 600),
            "en-US",
            Theme.DARK,
            1_700_000_000_000,
        )

    private fun validate(document: LiveMatchDocument) {
        require(document.payload.bytes.isNotEmpty())
        require(document.envelope.verifies(document.payload))
    }

    private fun manifest(document: LiveMatchDocument) =
        with(document.metadata) {
            "creator=$creator\nversion=$version\nviewport=${viewport.widthPx}x${viewport.heightPx}\nlocale=$localeTag\ntheme=$theme\ngenerationEpochMillis=$generationEpochMillis\nsha256=${document.envelope.sha256}\nsigningBoundary=${document.envelope.signingBoundary}\n"
        }
}
