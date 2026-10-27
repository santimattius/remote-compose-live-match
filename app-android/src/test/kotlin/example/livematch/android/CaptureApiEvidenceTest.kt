package example.livematch.android

import android.content.Context
import androidx.compose.remote.core.RemoteClock
import androidx.compose.remote.creation.compose.ExperimentalRemoteCreationComposeApi
import androidx.compose.remote.creation.compose.capture.CapturedDocument
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensity
import androidx.compose.remote.creation.compose.capture.WriterEvents
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalRemoteCreationComposeApi::class)
class CaptureApiEvidenceTest {
    @Test
    fun captureSingleRemoteDocumentExposesAndroidContextOverload() {
        val overloads =
            Class
                .forName("androidx.compose.remote.creation.compose.capture.CaptureRemoteDocumentKt")
                .methods
                .filter { it.name == "captureSingleRemoteDocument" }

        assertTrue(overloads.any { it.parameterTypes.firstOrNull() == Context::class.java })
    }
}
