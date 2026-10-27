package example.livematch.android

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals

class FileLastKnownGoodDocumentStorageTest {
    @Test
    fun `persists and atomically replaces the last known good snapshot`() {
        val directory = createTempDirectory("live-match-cache")
        try {
            val storage = FileLastKnownGoodDocumentStorage(directory.resolve("last-known-good.rc"))

            storage.write(byteArrayOf(1, 2, 3))
            assertContentEquals(byteArrayOf(1, 2, 3), FileLastKnownGoodDocumentStorage(directory.resolve("last-known-good.rc")).read())

            storage.write(byteArrayOf(4, 5))
            assertContentEquals(byteArrayOf(4, 5), storage.read())
            check(Files.list(directory).use { entries -> entries.count() } == 1L)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `persists a snapshot addressed by a relative path without a parent directory`() {
        val name = "relative-lkg-${System.nanoTime()}.bin"
        try {
            val storage = FileLastKnownGoodDocumentStorage(java.nio.file.Path.of(name))

            storage.write(byteArrayOf(9, 8))

            assertContentEquals(byteArrayOf(9, 8), storage.read())
        } finally {
            Files.deleteIfExists(java.nio.file.Path.of(name))
        }
    }
}
