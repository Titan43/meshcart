package com.meshcart.ratchet.domain

import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId

interface X3dhPort {
    /** Public bundle to advertise to peers. */
    fun generateBundle(identity: Identity): X3dhBundle
    /** Same as generateBundle but also returns the SPK private key needed for initAsRecipient. */
    fun generateBundleWithPrivateKey(identity: Identity): Pair<X3dhBundle, ByteArray>
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