package example.livematch.creator

import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import java.nio.file.Files
import java.nio.file.Path

object DiagnosticFixtureGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args.firstOrNull() ?: "fixtures")
        val fixtures =
            listOf(
                root.resolve("diagnostic/paint-probe.rc") to create(),
                root.resolve("diagnostic/alpha18-api6-control.rc") to createAlpha18Api6Control(),
            )
        if (args.contains("--check")) {
            fixtures.forEach { (fixture, bytes) ->
                require(Files.exists(fixture) && Files.readAllBytes(fixture).contentEquals(bytes)) {
                    "Stale diagnostic fixture: $fixture"
                }
            }
        } else {
            fixtures.forEach { (fixture, bytes) ->
                Files.createDirectories(fixture.parent)
                Files.write(fixture, bytes)
            }
        }
    }

    fun create(): ByteArray =
        RemoteComposeWriter(WIDTH, HEIGHT, "paint-probe-alpha18", JvmRcPlatformServices())
            .apply {
                getRcPaint().setColor(NAVY).setAntiAlias(true).commit()
                drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat())
                getRcPaint().setColor(YELLOW).setAntiAlias(true).commit()
                drawCircle(WIDTH / 2f, HEIGHT / 2f, CIRCLE_RADIUS)
            }.encodeToByteArray()

    /** Exact alpha18 [BasicProceduralDemos.simple1] direct-paint sequence. */
    fun createAlpha18Api6Control(): ByteArray =
        RemoteComposeWriter(600, 600, "Clock", 6, 0, JvmRcPlatformServices())
            .apply { drawCircle(150f, 150f, 150f) }
            .encodeToByteArray()

    private const val WIDTH = 320
    private const val HEIGHT = 180
    private const val CIRCLE_RADIUS = 64f
    private const val NAVY = 0xFF002B5C.toInt()
    private const val YELLOW = 0xFFFFFF00.toInt()
}
