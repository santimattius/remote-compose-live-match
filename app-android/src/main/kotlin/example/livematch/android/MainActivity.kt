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
        RemoteComposePlayerAdapter().load(player, document)
    }

    private companion object {
        const val SCHEDULED_FIXTURE_NAME = "scheduled-match.rc"
    }
}
