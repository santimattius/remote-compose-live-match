package example.livematch.android

import android.content.Context
import java.io.IOException

/** File name of the last-known-good snapshot inside the app's private files directory. */
internal const val LAST_KNOWN_GOOD_SNAPSHOT_NAME = "last-known-good-match.bin"

/** Where the launcher gets the signed envelope it tries to play, or null when none is available. */
internal fun interface CandidateSource {
    fun read(context: Context): ByteArray?
}

/** The envelope packaged in the APK, standing in for one downloaded from a backend. */
internal object PackagedCandidate : CandidateSource {
    private const val ASSET_NAME = "scheduled-match.rce"

    override fun read(context: Context): ByteArray? =
        try {
            context.assets.open(ASSET_NAME).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }
}

/**
 * Test seam: instrumentation replaces [current] to simulate being offline or receiving a bad update.
 * Production never changes it.
 */
internal object CandidateSources {
    @Volatile
    var current: CandidateSource = PackagedCandidate
}
