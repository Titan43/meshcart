package com.meshcart.ratchet.crypto

import com.meshcart.identity.crypto.Signature
import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId
import com.meshcart.ratchet.domain.*

class X3dhAdapter : X3dhPort {

    override fun generateBundle(identity: Identity): X3dhBundle =
        generateBundleWithPrivateKey(identity).first

    override fun generateBundleWithPrivateKey(identity: Identity): Pair<X3dhBundle, ByteArray> {
        val (spkPriv, spkPub) = newX25519KeyPair()
        val (_, otpkPub) = newX25519KeyPair()
        val signature = identity.sign(spkPub)
        val bundle = X3dhBundle(
            identityPublic = identity.encryptionPublicKey.bytes,
            signedPreKeyPublic = spkPub,
            signedPreKeySignature = signature.bytes,
            oneTimePreKeyPublic = otpkPub
        )
        return bundle to spkPriv
    }

    override fun initAsSender(
        identity: Identity,
        recipientBundle: X3dhBundle
    ): Pair<RatchetSession, X3dhInitMessage> {
        if (!identity.verify(
                recipientBundle.signedPreKeyPublic,
                Signature(recipientBundle.signedPreKeySignature)
            )
        ) throw RatchetException("Invalid signed prekey signature")

        val (ekPriv, ekPub) = newX25519KeyPair()
        val dh1 = dh(identity.encryptionPublicKey.bytes, recipientBundle.signedPreKeyPublic)
        val dh2 = dh(ekPriv, recipientBundle.identityPublic)
        val dh3 = dh(ekPriv, recipientBundle.signedPreKeyPublic)
        val dh4 = dh(ekPriv, recipientBundle.oneTimePreKeyPublic)

        val master = hkdf(dh1 + dh2 + dh3 + dh4, ByteArray(32), "meshcart-x3dh", 64)
        val (ratchetPriv, ratchetPub) = newX25519KeyPair()
        val (newRoot, sendChain) = kdfRootKey(master.copyOfRange(0, 32), dh(ratchetPriv, recipientBundle.signedPreKeyPublic))

        val session = RatchetSession(
            peerId = NodeId(recipientBundle.identityPublic.toHex()),
            rootKey = RootKey(newRoot),
            sendChainKey = ChainKey(sendChain),
            recvChainKey = ChainKey(master.copyOfRange(32, 64)),
            sendRatchetPrivate = ratchetPriv,
            sendRatchetPublic = ratchetPub,
            recvRatchetPublic = recipientBundle.signedPreKeyPublic
        )
        return session to X3dhInitMessage(ekPub, identity.encryptionPublicKey.bytes)
    }

    override fun initAsRecipient(
        identity: Identity,
        initMessage: X3dhInitMessage,
        localBundle: X3dhBundle,
        localSignedPreKeyPrivate: ByteArray,
        localOneTimePreKeyPrivate: ByteArray
    ): RatchetSession {
        val dh1 = dh(localSignedPreKeyPrivate, initMessage.senderIdentityPublic)
        val dh2 = dh(identity.encryptionPublicKey.bytes, initMessage.ephemeralPublic)
        val dh3 = dh(localSignedPreKeyPrivate, initMessage.ephemeralPublic)
        val dh4 = dh(localOneTimePreKeyPrivate, initMessage.ephemeralPublic)

        val master = hkdf(dh1 + dh2 + dh3 + dh4, ByteArray(32), "meshcart-x3dh", 64)
        val (ratchetPriv, ratchetPub) = newX25519KeyPair()
        val (newRoot, recvChain) = kdfRootKey(master.copyOfRange(0, 32), dh(localSignedPreKeyPrivate, initMessage.ephemeralPublic))

        return RatchetSession(
            peerId = NodeId(initMessage.senderIdentityPublic.toHex()),
            rootKey = RootKey(newRoot),
            sendChainKey = ChainKey(master.copyOfRange(32, 64)),
            recvChainKey = ChainKey(recvChain),
            sendRatchetPrivate = ratchetPriv,
            sendRatchetPublic = ratchetPub,
            recvRatchetPublic = initMessage.ephemeralPublic
        )
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}