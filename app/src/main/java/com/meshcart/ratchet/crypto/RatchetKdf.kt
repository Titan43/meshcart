package com.meshcart.ratchet.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

internal fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
    val agreement = X25519Agreement()
    agreement.init(X25519PrivateKeyParameters(privateKey, 0))
    return ByteArray(agreement.agreementSize).also {
        agreement.calculateAgreement(X25519PublicKeyParameters(publicKey, 0), it, 0)
    }
}

internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = 32): ByteArray {
    val gen = HKDFBytesGenerator(SHA256Digest())
    gen.init(HKDFParameters(ikm, salt, info.toByteArray()))
    return ByteArray(length).also { gen.generateBytes(it, 0, length) }
}

internal fun kdfRootKey(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
    val out = hkdf(dhOutput, rootKey, "meshcart-ratchet-root", 64)
    return out.copyOfRange(0, 32) to out.copyOfRange(32, 64)
}

internal fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
    val msgKey = hkdf(chainKey, byteArrayOf(0x01), "meshcart-message-key")
    val nextChain = hkdf(chainKey, byteArrayOf(0x02), "meshcart-chain-key")
    return nextChain to msgKey
}

internal fun newX25519KeyPair(): Pair<ByteArray, ByteArray> {
    val priv = X25519PrivateKeyParameters(java.security.SecureRandom())
    return priv.encoded to priv.generatePublicKey().encoded
}