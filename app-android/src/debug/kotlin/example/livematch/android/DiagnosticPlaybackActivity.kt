package example.livematch.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import example.livematch.contract.RemoteDocumentPayload

class DiagnosticPlaybackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = requireNotNull(intent.getStringExtra(MODE_EXTRA)) { "Missing diagnostic mode." }
        val fixture = intent.getStringExtra(FIXTURE_EXTRA) ?: PAINT_PROBE_FIXTURE_NAME
        require(fixture in DIAGNOSTIC_FIXTURE_NAMES) { "Unsupported diagnostic fixture: $fixture" }
        require(mode == MODE_PRE_ATTACH || mode == MODE_POST_ATTACH) { "Unsupported diagnostic mode: $mode" }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                        factory = { context ->
                            val player = RemoteComposePlayer(context)
                            when (mode) {
                                MODE_PRE_ATTACH -> load(player, fixture)
                                MODE_POST_ATTACH -> player.post { load(player, fixture) }
                            }
                            player
                        },
                    )
                }
            }
        }
    }

    private fun load(
        player: RemoteComposePlayer,
        fixture: String,
    ) {
        val bytes = assets.open(fixture).use { it.readBytes() }
        RemoteComposePlayerAdapter().load(player, RemoteDocumentPayload.from(bytes, bytes.size))
    }

    companion object {
        const val MODE_EXTRA = "mode"
        const val FIXTURE_EXTRA = "fixture"
        const val MODE_PRE_ATTACH = "pre_attach"
        const val MODE_POST_ATTACH = "post_attach"
        const val PAINT_PROBE_FIXTURE_NAME = "paint-probe.rc"
        const val ALPHA18_API6_CONTROL_FIXTURE_NAME = "alpha18-api6-control.rc"
        const val LIVE_MATCH_FIXTURE_NAME = "live-match.rc"
        const val FINISHED_MATCH_FIXTURE_NAME = "finished-match.rc"
        private val DIAGNOSTIC_FIXTURE_NAMES =
            setOf(
                PAINT_PROBE_FIXTURE_NAME,
                ALPHA18_API6_CONTROL_FIXTURE_NAME,
                LIVE_MATCH_FIXTURE_NAME,
                FINISHED_MATCH_FIXTURE_NAME,
            )
    }
}
