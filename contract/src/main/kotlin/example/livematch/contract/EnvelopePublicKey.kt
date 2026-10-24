package example.livematch.contract

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Ed25519 signature verification for [RemoteUiEnvelope]s. [PRODUCTION_PUBLIC_KEY_BASE64] is a
 * public key, safe to commit. The matching private key must never enter this repository -
 * see keys/README.md for how it is generated and kept out of version control.
 */
object EnvelopePublicKey {
    const val ALGORITHM = "Ed25519"

    // X.509/SPKI DER, base64-encoded. Generated once via keys/README.md's instructions.
    const val PRODUCTION_PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAciPwULtWEl+Wor4bWHsuAZSwICQ/sYNXWDZyjPPloWE="

    fun verify(
        signedBytes: ByteArray,
        signatureBase64: String,
        publicKeyBase64: String = PRODUCTION_PUBLIC_KEY_BASE64,
    ): Boolean =
        try {
            val publicKey =
                KeyFactory
                    .getInstance(ALGORITHM)
                    .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(signedBytes)
            verifier.verify(Base64.getDecoder().decode(signatureBase64))
        } catch (malformed: IllegalArgumentException) {
            false
        } catch (invalidKeyOrSignature: java.security.GeneralSecurityException) {
            false
        }

    /**
     * Converts the DER SubjectPublicKeyInfo form committed in the envelope contract into the
     * 32-byte form required by raw Ed25519 implementations. The exact DER prefix and total
     * length are checked before extracting the key; this is not unchecked SPKI truncation.
     */
    fun rawEd25519PublicKey(publicKeyBase64: String = PRODUCTION_PUBLIC_KEY_BASE64): ByteArray {
        val subjectPublicKeyInfo = Base64.getDecoder().decode(publicKeyBase64)
        require(subjectPublicKeyInfo.size == ED25519_SPKI_PREFIX.size + ED25519_PUBLIC_KEY_LENGTH) {
            "Ed25519 SPKI must be exactly ${ED25519_SPKI_PREFIX.size + ED25519_PUBLIC_KEY_LENGTH} bytes"
        }
        require(subjectPublicKeyInfo.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX)) {
            "Ed25519 SPKI has an invalid prefix"
        }
        return subjectPublicKeyInfo.copyOfRange(ED25519_SPKI_PREFIX.size, subjectPublicKeyInfo.size)
    }

    private const val ED25519_PUBLIC_KEY_LENGTH = 32
    private val ED25519_SPKI_PREFIX =
        byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
}
