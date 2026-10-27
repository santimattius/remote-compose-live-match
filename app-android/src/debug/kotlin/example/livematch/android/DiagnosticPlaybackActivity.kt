package example.livematch.android

import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import example.livematch.contract.ActionId
import example.livematch.contract.MatchPhase
import example.livematch.contract.MatchPresentation
import example.livematch.contract.RemoteDocumentPayload
import kotlinx.coroutines.launch
import java.time.Instant

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
        val phase = PHASE_BY_FIXTURE[fixture]
        if (phase == null) {
            RemoteComposePlayerAdapter().load(player, RemoteDocumentPayload.from(bytes, bytes.size))
            return
        }
        // Signed fixtures carry match data; the document is captured on-device, so loading is asynchronous.
        lifecycleScope.launch {
            val loaded = EnvelopeMatchLoader().load(bytes, Instant.now().epochSecond)
            if (loaded !is MatchEnvelopeLoadResult.Loaded) {
                Log.w(TAG, "Rejected diagnostic fixture envelope: $loaded")
                return@launch
            }
            val document = compile(loaded.presentation)
            RemoteComposePlayerAdapter().load(player, document)
            // Must register after setDocument, not before; see MainActivity and
            // docs/alpha-learnings.md#register-action-listeners-after-loading-the-document.
            val dispatcher = HostActionDispatcher(loaded.presentation.matchId) { Log.i(TAG, "Host navigation: $it") }
            player.addIdActionListener { _, metadata -> onDocumentAction(dispatcher, phase, metadata) }
        }
    }

    private suspend fun compile(presentation: MatchPresentation): RemoteDocumentPayload =
        with(AndroidMatchDocumentCompiler(this)) {
            when (presentation) {
                is MatchPresentation.Scheduled -> compile(presentation.match)
                is MatchPresentation.Live -> compile(presentation.match)
                is MatchPresentation.Finished -> compile(presentation.match)
            }
        }

    /** Exposed for instrumentation tests to observe dispatch outcomes without parsing logcat. */
    internal var lastDispatchedAction: ActionId? = null
        private set

    private fun onDocumentAction(
        dispatcher: HostActionDispatcher,
        phase: MatchPhase,
        wireValue: String?,
    ) {
        when (dispatcher.dispatch(phase, wireValue)) {
            HostActionDispatchResult.Handled -> {
                lastDispatchedAction = wireValue?.let(ActionId::fromWireValue)
                Log.i(TAG, "Accepted action: $wireValue")
            }

            HostActionDispatchResult.Rejected -> {
                Log.w(TAG, "Rejected action: $wireValue")
            }
        }
    }

    companion object {
        const val MODE_EXTRA = "mode"
        const val FIXTURE_EXTRA = "fixture"
        const val MODE_PRE_ATTACH = "pre_attach"
        const val MODE_POST_ATTACH = "post_attach"
        const val PAINT_PROBE_FIXTURE_NAME = "paint-probe.rc"
        const val ALPHA18_API6_CONTROL_FIXTURE_NAME = "alpha18-api6-control.rc"
        const val LIVE_MATCH_FIXTURE_NAME = "live-match.rce"
        const val FINISHED_MATCH_FIXTURE_NAME = "finished-match.rce"
        private const val TAG = "DiagnosticPlayback"
        private val DIAGNOSTIC_FIXTURE_NAMES =
            setOf(
                PAINT_PROBE_FIXTURE_NAME,
                ALPHA18_API6_CONTROL_FIXTURE_NAME,
                LIVE_MATCH_FIXTURE_NAME,
                FINISHED_MATCH_FIXTURE_NAME,
            )
        private val PHASE_BY_FIXTURE =
            mapOf(
                LIVE_MATCH_FIXTURE_NAME to MatchPhase.LIVE,
                FINISHED_MATCH_FIXTURE_NAME to MatchPhase.FINISHED,
            )
    }
}
