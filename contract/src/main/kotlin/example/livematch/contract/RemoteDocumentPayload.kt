package example.livematch.contract

class RemoteDocumentPayload private constructor(
    private val contents: ByteArray,
) {
    val bytes: ByteArray
        get() = contents.copyOf()

    companion object {
        fun from(
            bytes: ByteArray,
            declaredSize: Int,
        ): RemoteDocumentPayload {
            require(bytes.isNotEmpty()) { "Remote Compose document must not be empty." }
            require(bytes.size == declaredSize) { "Remote Compose document was truncated in transit." }
            return RemoteDocumentPayload(bytes.copyOf())
        }
    }
}
