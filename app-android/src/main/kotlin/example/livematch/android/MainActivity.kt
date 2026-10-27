package example.livematch.android

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import example.livematch.contract.ActionId
import example.livematch.contract.MatchPhase
import example.livematch.contract.MatchPresentation
import example.livematch.contract.RemoteDocumentPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val compiler by lazy { AndroidMatchDocumentCompiler(this) }
    private val resolver by lazy {
        MatchPlaybackResolver(
            repository =
                LastKnownGoodDocumentRepository(
                    FileLastKnownGoodDocumentStorage(filesDir.toPath().resolve(LAST_KNOWN_GOOD_SNAPSHOT_NAME)),
                ),
            compile = ::compile,
            onFailure = { Log.w(TAG, "Could not play match data", it) },
        )
    }

    /** Exposed for instrumentation tests to observe dispatch outcomes without parsing logcat. */
    internal var lastDispatchedAction: ActionId? = null
        private set

    internal var lastDestination: HostDestination? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var playback by remember { mutableStateOf<MatchPlayback?>(null) }
            LaunchedEffect(Unit) { playback = withContext(Dispatchers.IO) { resolvePlayback() } }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val current = playback) {
                        is MatchPlayback.Playable -> {
                            PlayablePlayer(current)
                        }

                        MatchPlayback.LocalFallback -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Match information is unavailable right now.")
                            }
                        }

                        null -> {
                            Unit
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun PlayablePlayer(playable: MatchPlayback.Playable) {
        AndroidView(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            factory = { context ->
                RemoteComposePlayer(context).also { player ->
                    RemoteComposePlayerAdapter().load(player, playable.document)
                    // Must register after setDocument: RemoteComposeView.addIdActionListener() attaches
                    // directly to the *current* CoreDocument instance, and setDocument() replaces it with
                    // a new one. Registering first orphans the listener on the discarded document (WU9).
                    val dispatcher = HostActionDispatcher(playable.presentation.matchId, ::navigate)
                    val phase = playable.presentation.phase()
                    player.addIdActionListener { _, metadata -> onDocumentAction(dispatcher, phase, metadata) }
                    player.exposeScreenReaderActions(MatchCardActions.of(playable.presentation)) {
                        onDocumentAction(dispatcher, phase, it.wireValue)
                    }
                }
            },
        )
    }

    private suspend fun resolvePlayback(): MatchPlayback =
        resolver.resolve(CandidateSources.current.read(this), Instant.now().epochSecond)

    private suspend fun compile(presentation: MatchPresentation): RemoteDocumentPayload =
        when (presentation) {
            is MatchPresentation.Scheduled -> compiler.compile(presentation.match)
            is MatchPresentation.Live -> compiler.compile(presentation.match)
            is MatchPresentation.Finished -> compiler.compile(presentation.match)
        }

    /** Host validates state before acting, per spec section 6.3 - unknown/disallowed ids never reach domain logic. */
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

    private fun navigate(destination: HostDestination) {
        lastDestination = destination
        Toast.makeText(this, "Host handled ${destination::class.simpleName} for ${destination.matchId}", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
