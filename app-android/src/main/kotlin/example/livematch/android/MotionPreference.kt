package example.livematch.android

import android.content.Context
import android.provider.Settings

/**
 * The user's motion preference, resolved by the host because the Remote Compose document format
 * exposes no reduced-motion variable. The document is captured on-device, so the preference is
 * applied when it is captured; a change takes effect the next time a document is compiled.
 */
object MotionPreference {
    /** Android's "remove animations" setting sets the global animator duration scale to zero. */
    fun isReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

    fun isReduced(context: Context): Boolean =
        isReduced(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
}
