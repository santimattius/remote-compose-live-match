package example.livematch.creator

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiagnosticFixtureGeneratorTest {
    @Test
    fun alpha18Api6ControlIsDeterministicAndDistinctFromExistingFixtures() {
        val first = DiagnosticFixtureGenerator.createAlpha18Api6Control()
        val second = DiagnosticFixtureGenerator.createAlpha18Api6Control()

        assertTrue(first.isNotEmpty())
        assertContentEquals(first, second)
        assertNotEquals(EXPECTED_SHA256, sha256(first))
        assertNotEquals(CANONICAL_SCHEDULED_SHA256, sha256(first))
    }

    @Test
    fun diagnosticCreatorEmitsAStableNonCanonicalPaintProbe() {
        val root = Files.createTempDirectory("paint-probe")
        try {
            DiagnosticFixtureGenerator.main(arrayOf(root.toString()))
            val fixture = root.resolve("diagnostic/paint-probe.rc")
            val first = Files.readAllBytes(fixture)

            DiagnosticFixtureGenerator.main(arrayOf(root.toString()))
            val second = Files.readAllBytes(fixture)

            assertTrue(first.isNotEmpty())
            assertContentEquals(first, second)
            assertTrue(sha256(first) == EXPECTED_SHA256)
            assertFalse(sha256(first) == CANONICAL_SCHEDULED_SHA256)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val EXPECTED_SHA256 = "4a3b1f4c4fe2c0c00153fa4655bfc2ada94564abd1fde6edfd0bcf4ffe0011e6"
        const val CANONICAL_SCHEDULED_SHA256 = "57884b403add2d49825b89d71e6257fbc5a15cd66f51581890a8442b54741d51"
    }
}
