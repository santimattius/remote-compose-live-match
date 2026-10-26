package example.livematch.creator

import example.livematch.contract.EnvelopePublicKey
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Loads the private signing key from outside the repository. Fixture generation reads it from
 * an environment variable; see keys/README.md for how to generate a local dev key and export it.
 */
object EnvelopeSigningKeyLoader {
    const val DEFAULT_ENV_VAR: String = "LIVE_MATCH_SIGNING_PRIVATE_KEY"

    fun loadFromEnv(envVar: String = DEFAULT_ENV_VAR): PrivateKey {
        val base64 =
            System.getenv(envVar)
                ?: error("Missing $envVar - see keys/README.md to generate a local dev signing key.")
        return decode(base64)
    }

    fun decode(pkcs8Base64: String): PrivateKey {
        val keyFactory = KeyFactory.getInstance(EnvelopePublicKey.ALGORITHM)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pkcs8Base64)))
    }
}
