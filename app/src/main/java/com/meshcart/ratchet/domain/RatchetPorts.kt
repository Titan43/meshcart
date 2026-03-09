package com.meshcart.ratchet.domain

import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId

interface X3dhPort {
    fun generateBundle(identity: Identity): X3dhBundle
    fun initAsSender(identity: Identity, recipientBundle: X3dhBundle): Pair<RatchetSession, X3dhInitMessage>
    fun initAsRecipient(identity: Identity, initMessage: X3dhInitMessage, localBundle: X3dhBundle, localSignedPreKeyPrivate: ByteArray, localOneTimePreKeyPrivate: ByteArray): RatchetSession
}

interface RatchetPort {
    fun encrypt(session: RatchetSession, plaintext: ByteArray): Pair<RatchetSession, RatchetMessage>
    fun decrypt(session: RatchetSession, message: RatchetMessage): Pair<RatchetSession, ByteArray>
}

interface RatchetSessionStoragePort {
    fun load(peerId: NodeId): RatchetSession?
    fun save(session: RatchetSession)
    fun delete(peerId: NodeId)
}