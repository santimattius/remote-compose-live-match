package example.livematch.android

import androidx.compose.remote.player.view.RemoteComposePlayer
import example.livematch.contract.RemoteDocumentPayload

class RemoteComposePlayerAdapter {
    fun load(
        player: RemoteComposePlayer,
        document: RemoteDocumentPayload,
    ) {
        player.setDocument(document.bytes)
    }
}
