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
            val keyFactory = KeyFactory.getInstance(ALGORITHM)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(signedBytes)
            verifier.verify(Base64.getDecoder().decode(signatureBase64))
        } catch (malformed: IllegalArgumentException) {
            false
        } catch (invalidKeyOrSignature: java.security.GeneralSecurityException) {
            false
        }
}
