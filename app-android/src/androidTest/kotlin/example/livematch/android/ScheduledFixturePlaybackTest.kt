package example.livematch.android

import android.view.ViewGroup
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ScheduledFixturePlaybackTest {
    @Test
    fun exactScheduledFixtureIsPackagedAndInstalledInAResumedPlayer() {
        val fixture =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .assets
                .open(FIXTURE_NAME)
                .use { it.readBytes() }

        assertEquals(EXPECTED_SHA256, sha256(fixture))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val player = findRemoteComposePlayer(root)
                assertTrue(player != null)
                assertTrue(player!!.width > 0 && player.height > 0)
            }
        }
    }

    private fun findRemoteComposePlayer(view: android.view.View): RemoteComposePlayer? {
        if (view is RemoteComposePlayer) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findRemoteComposePlayer(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE_NAME = "scheduled-match.rce"
        const val EXPECTED_SHA256 = "603f4fc6b27ceeb3c7dee6d4da96f5631448b2657f69befb6150bad709b89719"
    }
}
