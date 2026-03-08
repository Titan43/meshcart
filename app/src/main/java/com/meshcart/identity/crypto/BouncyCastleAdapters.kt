package com.meshcart.identity.crypto

import com.meshcart.identity.domain.SharedSecret
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

private fun hkdf(ikm: ByteArray, info: String): ByteArray {
    val gen = HKDFBytesGenerator(SHA256Digest())
    gen.init(HKDFParameters(ikm, "meshcart-v1".toByteArray(), info.toByteArray()))
    return ByteArray(32).also { gen.generateBytes(it, 0, 32) }
}

class BouncyCastleSignerAdapter : SignerPort {

    override fun generateKeyPair() = deriveKeyPair(ByteArray(32).also { SecureRandom().nextBytes(it) })

    override fun deriveKeyPair(seed: ByteArray): SigningKeyPair {
        val priv = Ed25519PrivateKeyParameters(hkdf(seed, "meshcart/signing/v1"), 0)
        return SigningKeyPair(SigningPrivateKey(priv.encoded), SigningPublicKey(priv.generatePublicKey().encoded))
    }

    override fun sign(privateKey: SigningPrivateKey, message: ByteArray): Signature {
        val signer = Ed25519Signer().apply { init(true, Ed25519PrivateKeyParameters(privateKey.bytes, 0)) }
        signer.update(message, 0, message.size)
        return Signature(signer.generateSignature())
    }

    override fun verify(publicKey: SigningPublicKey, message: ByteArray, signature: Signature): Boolean {
        val verifier = Ed25519Signer().apply { init(false, Ed25519PublicKeyParameters(publicKey.bytes, 0)) }
        verifier.update(message, 0, message.size)
        return runCatching { verifier.verifySignature(signature.bytes) }.getOrDefault(false)
    }
}

class BouncyCastleKeyAgreementAdapter : KeyAgreementPort {

    override fun generateKeyPair() = deriveKeyPair(ByteArray(32).also { SecureRandom().nextBytes(it) })

    override fun deriveKeyPair(seed: ByteArray): EncryptionKeyPair {
        val priv = X25519PrivateKeyParameters(hkdf(seed, "meshcart/encryption/v1"), 0)
        return EncryptionKeyPair(EncryptionPrivateKey(priv.encoded), EncryptionPublicKey(priv.generatePublicKey().encoded))
    }

    override fun sharedSecret(ourPrivate: EncryptionPrivateKey, theirPublic: EncryptionPublicKey): SharedSecret {
        val agreement = X25519Agreement().apply { init(X25519PrivateKeyParameters(ourPrivate.bytes, 0)) }
        return SharedSecret(ByteArray(agreement.agreementSize).also {
            agreement.calculateAgreement(X25519PublicKeyParameters(theirPublic.bytes, 0), it, 0)
        })
    }
}