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
import example.livematch.contract.ActionAllowlist
import example.livematch.contract.ActionId
import example.livematch.contract.MatchPhase
import example.livematch.contract.RemoteDocumentPayload

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                        factory = { context ->
                            RemoteComposePlayer(context).also { player -> loadScheduledFixture(player) }
                        },
                    )
                }
            }
        }
    }

    private fun loadScheduledFixture(player: RemoteComposePlayer) {
        val bytes = assets.open(SCHEDULED_FIXTURE_NAME).use { it.readBytes() }
        val document = RemoteDocumentPayload.from(bytes, bytes.size)
        // Must register after setDocument: RemoteComposeView.addIdActionListener() attaches
        // directly to the *current* CoreDocument instance, and setDocument() replaces it with
        // a new one. Registering first orphans the listener on the discarded document (WU9).
        RemoteComposePlayerAdapter().load(player, document)
        player.addIdActionListener { _, metadata -> onDocumentAction(metadata) }
    }

    /** Exposed for instrumentation tests to observe dispatch outcomes without parsing logcat. */
    internal var lastDispatchedAction: ActionId? = null
        private set

    /** Host validates state before acting, per spec section 6.3 - unknown/disallowed ids never reach domain logic. */
    private fun onDocumentAction(wireValue: String?) {
        val actionId = wireValue?.let(ActionId::fromWireValue)
        when {
            actionId == null -> Log.w(TAG, "Ignoring unknown action id: $wireValue")
            ActionAllowlist.isAllowed(CURRENT_PHASE, actionId) -> {
                lastDispatchedAction = actionId
                Log.i(TAG, "Accepted action: ${actionId.wireValue}")
            }
            else -> Log.w(TAG, "Rejected action not allowed in $CURRENT_PHASE: ${actionId.wireValue}")
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val SCHEDULED_FIXTURE_NAME = "scheduled-match.rc"
        val CURRENT_PHASE = MatchPhase.SCHEDULED
    }
}
