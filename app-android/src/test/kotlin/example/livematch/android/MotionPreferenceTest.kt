package example.livematch.android

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionPreferenceTest {
    @Test
    fun `removing animations reduces motion`() {
        assertTrue(MotionPreference.isReduced(0f))
    }

    @Test
    fun `any running animation scale keeps motion`() {
        assertFalse(MotionPreference.isReduced(1f))
        assertFalse(MotionPreference.isReduced(0.5f))
        assertFalse(MotionPreference.isReduced(10f))
    }
}
