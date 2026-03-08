package com.meshcart.identity.crypto

@JvmInline value class SigningPrivateKey(val bytes: ByteArray) { override fun toString() = "SigningPrivateKey[redacted]" }
@JvmInline value class SigningPublicKey(val bytes: ByteArray)
@JvmInline value class EncryptionPrivateKey(val bytes: ByteArray) { override fun toString() = "EncryptionPrivateKey[redacted]" }
@JvmInline value class EncryptionPublicKey(val bytes: ByteArray)
@JvmInline value class Signature(val bytes: ByteArray)

data class SigningKeyPair(val private: SigningPrivateKey, val public: SigningPublicKey)
data class EncryptionKeyPair(val private: EncryptionPrivateKey, val public: EncryptionPublicKey)