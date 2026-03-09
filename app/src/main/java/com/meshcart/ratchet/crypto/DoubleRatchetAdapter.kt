package com.meshcart.ratchet.crypto

import com.meshcart.ratchet.domain.*

class DoubleRatchetAdapter : RatchetPort {

    private val maxSkip = 1000

    override fun encrypt(session: RatchetSession, plaintext: ByteArray): Pair<RatchetSession, RatchetMessage> {
        val (nextChain, msgKey) = kdfChainKey(session.sendChainKey.bytes)
        val header = RatchetHeader(session.sendRatchetPublic, session.prevSendCount, session.sendCount)
        val ciphertext = chaChaEncrypt(msgKey, header.encode(), plaintext)
        return session.copy(
            sendChainKey = ChainKey(nextChain),
            sendCount = session.sendCount + 1
        ) to RatchetMessage(header, ciphertext)
    }

    override fun decrypt(session: RatchetSession, message: RatchetMessage): Pair<RatchetSession, ByteArray> {
        val skippedKey = session.skippedKeys[skippedKeyId(message.header.dhPublicKey, message.header.messageNumber)]
        if (skippedKey != null) {
            val plaintext = chaChaDecrypt(skippedKey, message.header.encode(), message.ciphertext)
            return session.copy(
                skippedKeys = session.skippedKeys - skippedKeyId(message.header.dhPublicKey, message.header.messageNumber)
            ) to plaintext
        }

        var current = session
        if (session.recvRatchetPublic == null || !message.header.dhPublicKey.contentEquals(session.recvRatchetPublic)) {
            current = skipMessageKeys(current, message.header.prevSendCount)
            current = dhRatchetStep(current, message.header.dhPublicKey)
        }
        current = skipMessageKeys(current, message.header.messageNumber)

        val (nextChain, msgKey) = kdfChainKey(current.recvChainKey.bytes)
        val plaintext = chaChaDecrypt(msgKey, message.header.encode(), message.ciphertext)
        return current.copy(
            recvChainKey = ChainKey(nextChain),
            recvCount = current.recvCount + 1
        ) to plaintext
    }

    private fun dhRatchetStep(session: RatchetSession, theirPublic: ByteArray): RatchetSession {
        val (newRoot1, recvChain) = kdfRootKey(session.rootKey.bytes, dh(session.sendRatchetPrivate, theirPublic))
        val (ratchetPriv, ratchetPub) = newX25519KeyPair()
        val (newRoot2, sendChain) = kdfRootKey(newRoot1, dh(ratchetPriv, theirPublic))
        return session.copy(
            rootKey = RootKey(newRoot2),
            sendChainKey = ChainKey(sendChain),
            recvChainKey = ChainKey(recvChain),
            sendRatchetPrivate = ratchetPriv,
            sendRatchetPublic = ratchetPub,
            recvRatchetPublic = theirPublic,
            prevSendCount = session.sendCount,
            sendCount = 0,
            recvCount = 0
        )
    }

    private fun skipMessageKeys(session: RatchetSession, until: Int): RatchetSession {
        if (session.recvCount + maxSkip < until) throw RatchetException("Too many skipped messages")
        var current = session
        val skipped = current.skippedKeys.toMutableMap()
        while (current.recvCount < until) {
            val (nextChain, msgKey) = kdfChainKey(current.recvChainKey.bytes)
            skipped[skippedKeyId(current.recvRatchetPublic!!, current.recvCount)] = msgKey
            current = current.copy(recvChainKey = ChainKey(nextChain), recvCount = current.recvCount + 1)
        }
        return current.copy(skippedKeys = skipped)
    }

    private fun skippedKeyId(dhPublic: ByteArray, index: Int) =
        "${dhPublic.toHex()}:$index"

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

internal fun RatchetHeader.encode(): ByteArray =
    dhPublicKey + prevSendCount.toBytes() + messageNumber.toBytes()

private fun Int.toBytes() = byteArrayOf(
    (this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte()
)