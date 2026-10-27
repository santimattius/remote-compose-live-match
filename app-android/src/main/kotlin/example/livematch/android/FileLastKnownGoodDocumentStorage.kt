package example.livematch.android

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** File-backed snapshot storage that replaces the active document only after a complete write. */
class FileLastKnownGoodDocumentStorage(
    private val snapshotPath: Path,
) : LastKnownGoodDocumentStorage {
    override fun read(): ByteArray? =
        if (Files.isRegularFile(snapshotPath)) {
            Files.readAllBytes(snapshotPath)
        } else {
            null
        }

    override fun write(documentBytes: ByteArray) {
        require(documentBytes.isNotEmpty()) { "Last-known-good document must not be empty." }
        val parent = snapshotPath.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, snapshotPath.fileName.toString(), ".pending")
        try {
            // Flush to disk before the rename so a power loss cannot leave an empty or partial snapshot.
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                channel.write(ByteBuffer.wrap(documentBytes))
                channel.force(true)
            }
            try {
                Files.move(
                    temporary,
                    snapshotPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, snapshotPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
