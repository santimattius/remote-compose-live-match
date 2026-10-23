package example.livematch.contract

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvelopePublicKeyTest {
    @Test
    fun `verifies a signature produced by the matching private key`() {
        val keyPair = KeyPairGenerator.getInstance(EnvelopePublicKey.ALGORITHM).generateKeyPair()
        val data = "canonical-input".toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance(EnvelopePublicKey.ALGORITHM).run {
            initSign(keyPair.private)
            update(data)
            Base64.getEncoder().encodeToString(sign())
        }
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        assertTrue(EnvelopePublicKey.verify(data, signature, publicKeyBase64))
    }

    @Test
    fun `rejects a signature from a different key pair`() {
        val signingKeyPair = KeyPairGenerator.getInstance(EnvelopePublicKey.ALGORITHM).generateKeyPair()
        val otherKeyPair = KeyPairGenerator.getInstance(EnvelopePublicKey.ALGORITHM).generateKeyPair()
        val data = "canonical-input".toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance(EnvelopePublicKey.ALGORITHM).run {
            initSign(signingKeyPair.private)
            update(data)
            Base64.getEncoder().encodeToString(sign())
        }
        val otherPublicKeyBase64 = Base64.getEncoder().encodeToString(otherKeyPair.public.encoded)

        assertFalse(EnvelopePublicKey.verify(data, signature, otherPublicKeyBase64))
    }

    @Test
    fun `rejects a signature over tampered data`() {
        val keyPair = KeyPairGenerator.getInstance(EnvelopePublicKey.ALGORITHM).generateKeyPair()
        val data = "canonical-input".toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance(EnvelopePublicKey.ALGORITHM).run {
            initSign(keyPair.private)
            update(data)
            Base64.getEncoder().encodeToString(sign())
        }
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        assertFalse(EnvelopePublicKey.verify("tampered-input".toByteArray(Charsets.UTF_8), signature, publicKeyBase64))
    }

    @Test
    fun `rejects malformed signature or key input instead of throwing`() {
        assertFalse(EnvelopePublicKey.verify(ByteArray(0), "not-base64!!", "also-not-base64!!"))
    }

    @Test
    fun `defaults to the production public key`() {
        assertTrue(EnvelopePublicKey.PRODUCTION_PUBLIC_KEY_BASE64.isNotBlank())
    }
}
