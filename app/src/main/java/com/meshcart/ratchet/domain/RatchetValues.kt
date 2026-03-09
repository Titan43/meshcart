package com.meshcart.ratchet.domain

import com.meshcart.identity.domain.NodeId

@JvmInline value class RootKey(val bytes: ByteArray) { override fun toString() = "RootKey[redacted]" }
@JvmInline value class ChainKey(val bytes: ByteArray) { override fun toString() = "ChainKey[redacted]" }
@JvmInline value class MessageKey(val bytes: ByteArray) { override fun toString() = "MessageKey[redacted]" }

data class RatchetHeader(
    val dhPublicKey: ByteArray,
    val prevSendCount: Int,
    val messageNumber: Int
)

data class RatchetMessage(
    val header: RatchetHeader,
    val ciphertext: ByteArray
)

data class RatchetSession(
    val peerId: NodeId,
    val rootKey: RootKey,
    val sendChainKey: ChainKey,
    val recvChainKey: ChainKey,
    val sendRatchetPrivate: ByteArray,
    val sendRatchetPublic: ByteArray,
    val recvRatchetPublic: ByteArray?,
    val sendCount: Int = 0,
    val recvCount: Int = 0,
    val prevSendCount: Int = 0,
    val skippedKeys: Map<String, ByteArray> = emptyMap()
) {
    override fun toString() = "RatchetSession(peerId=$peerId)"
}

data class X3dhBundle(
    val identityPublic: ByteArray,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyPublic: ByteArray
)

data class X3dhInitMessage(
    val ephemeralPublic: ByteArray,
    val senderIdentityPublic: ByteArray
)

class RatchetException(message: String, cause: Throwable? = null) : Exception(message, cause)