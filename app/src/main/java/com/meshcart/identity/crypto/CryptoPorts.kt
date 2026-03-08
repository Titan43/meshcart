package com.meshcart.identity.crypto

import com.meshcart.identity.domain.SharedSecret

interface SignerPort {
    fun generateKeyPair(): SigningKeyPair
    fun deriveKeyPair(seed: ByteArray): SigningKeyPair
    fun sign(privateKey: SigningPrivateKey, message: ByteArray): Signature
    fun verify(publicKey: SigningPublicKey, message: ByteArray, signature: Signature): Boolean
}

interface KeyAgreementPort {
    fun generateKeyPair(): EncryptionKeyPair
    fun deriveKeyPair(seed: ByteArray): EncryptionKeyPair
    fun sharedSecret(ourPrivate: EncryptionPrivateKey, theirPublic: EncryptionPublicKey): SharedSecret
}