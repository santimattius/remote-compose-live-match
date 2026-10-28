package example.livematch.android

import example.livematch.contract.RemoteDocumentPayload

internal fun payloadOf(vararg bytes: Byte): RemoteDocumentPayload = RemoteDocumentPayload.from(bytes, bytes.size)

internal fun validated(
    payload: RemoteDocumentPayload,
    matchId: String = "match-1",
    revision: Long = 1,
    createdAt: Long = 1_000,
    expiresAt: Long = 2_000_000_000,
) = ValidatedMatch(SnapshotMetadata(matchId, revision, createdAt, expiresAt), payload)

internal fun snapshotBytes(match: ValidatedMatch): ByteArray = SnapshotCodec.encode(match)
