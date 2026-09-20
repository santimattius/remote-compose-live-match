package example.livematch.creator

import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter

class MatchDocumentCreator {
    fun create(title: String): ByteArray =
        RemoteComposeWriter(320, 180, title, JvmRcPlatformServices())
            .apply {
                drawRect(0f, 0f, 320f, 180f)
            }.encodeToByteArray()
}
