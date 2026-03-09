package com.meshcart.sync.domain

import com.meshcart.identity.domain.Identity
import com.meshcart.p2p.domain.MeshConnection
import com.meshcart.ratchet.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Wire types ────────────────────────────────────────────────────────────────

/** Unencrypted handshake envelope — carries X3DH public key material only. */
@Serializable
private data class WireHandshake(
    /** Sender's X3DH bundle so the recipient can complete the exchange. */
    val identityPublic: ByteArray,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyPublic: ByteArray,
    /** X3DH init message from the sender (empty if this is a bundle-only advertisement). */
    val ephemeralPublic: ByteArray? = null,
    val senderIdentityPublic: ByteArray? = null
)

/** Encrypted message envelope. */
@Serializable
private data class WireMessage(
    val dhPublicKey: ByteArray,
    val prevSendCount: Int,
    val messageNumber: Int,
    val ciphertext: ByteArray
) {
    fun toRatchetMessage() = RatchetMessage(
        header = RatchetHeader(dhPublicKey, prevSendCount, messageNumber),
        ciphertext = ciphertext
    )
}

/** Top-level frame — either a handshake or an encrypted message. */
@Serializable
private sealed class WireFrame {
    @Serializable @SerialName("hs") data class Handshake(val payload: WireHandshake) : WireFrame()
    @Serializable @SerialName("msg") data class Message(val payload: WireMessage) : WireFrame()
}

// ── SyncAdapter ───────────────────────────────────────────────────────────────

class SyncAdapter(
    private val identity: Identity,
    private val x3dh: X3dhPort,
    private val ratchet: RatchetPort,
    private val sessions: RatchetSessionStoragePort
) : SyncPort {

    // Stores the SPK private key generated for our bundle so initAsRecipient can use it.
    // Keyed by the public key hex to handle bundle rotation.
    private val pendingSpkPrivate = mutableMapOf<String, ByteArray>()

    /**
     * Send an encrypted [SyncMessage] to [connection].
     *
     * If no ratchet session exists yet this node acts as the X3DH **initiator**:
     * 1. Advertise our own bundle so the peer can reply to us later.
     * 2. Send an X3DH init message using a *stub* recipient bundle derived from
     *    the peer's known identity key (stored when they invited us / we invited them).
     *    The recipient completes the session when they receive the init message.
     */
    override suspend fun send(connection: MeshConnection, message: SyncMessage) {
        val session = sessions.load(connection.remoteNodeId) ?: run {
            initiateSession(connection)
            sessions.load(connection.remoteNodeId)
                ?: throw IllegalStateException(
                    "X3DH initiation failed for ${connection.remoteNodeId.value}"
                )
        }
        sendEncrypted(connection, session, message)
    }

    override fun receive(connection: MeshConnection): Flow<SyncMessage> =
        connection.incoming.mapNotNull { bytes ->
            val frame = runCatching {
                Json.decodeFromString<WireFrame>(String(bytes))
            }.getOrNull() ?: return@mapNotNull null

            when (frame) {
                is WireFrame.Handshake -> {
                    handleHandshake(connection, frame.payload)
                    null  // handshake is infrastructure — not a SyncMessage
                }
                is WireFrame.Message -> {
                    val session = sessions.load(connection.remoteNodeId)
                        ?: return@mapNotNull null
                    val (nextSession, plaintext) = runCatching {
                        ratchet.decrypt(session, frame.payload.toRatchetMessage())
                    }.getOrNull() ?: return@mapNotNull null
                    sessions.save(nextSession)
                    runCatching { Json.decodeFromString<SyncMessage>(String(plaintext)) }.getOrNull()
                }
            }
        }

    // ── private helpers ───────────────────────────────────────────────────────

    private suspend fun initiateSession(connection: MeshConnection) {
        val (localBundle, spkPriv) = x3dh.generateBundleWithPrivateKey(identity)
        pendingSpkPrivate[localBundle.signedPreKeyPublic.toHex()] = spkPriv

        // Advertise our bundle so the peer can respond with an X3DH init
        val advertisement = WireHandshake(
            identityPublic        = localBundle.identityPublic,
            signedPreKeyPublic    = localBundle.signedPreKeyPublic,
            signedPreKeySignature = localBundle.signedPreKeySignature,
            oneTimePreKeyPublic   = localBundle.oneTimePreKeyPublic
        )
        connection.send(Json.encodeToString<WireFrame>(WireFrame.Handshake(advertisement)).toByteArray())

        // Build a recipient bundle from the peer's known identity key.
        // The peer completes the session when they receive our init message.
        val peerIdBytes = connection.remoteNodeId.value
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val recipientBundle = X3dhBundle(
            identityPublic        = peerIdBytes,
            signedPreKeyPublic    = peerIdBytes,
            signedPreKeySignature = ByteArray(64),
            oneTimePreKeyPublic   = peerIdBytes
        )

        val (session, initMsg) = x3dh.initAsSender(identity, recipientBundle)
        sessions.save(session)

        val initHandshake = WireHandshake(
            identityPublic        = localBundle.identityPublic,
            signedPreKeyPublic    = localBundle.signedPreKeyPublic,
            signedPreKeySignature = localBundle.signedPreKeySignature,
            oneTimePreKeyPublic   = localBundle.oneTimePreKeyPublic,
            ephemeralPublic       = initMsg.ephemeralPublic,
            senderIdentityPublic  = initMsg.senderIdentityPublic
        )
        connection.send(Json.encodeToString<WireFrame>(WireFrame.Handshake(initHandshake)).toByteArray())
    }

    private fun handleHandshake(connection: MeshConnection, hs: WireHandshake) {
        if (hs.ephemeralPublic == null) return
        if (sessions.load(connection.remoteNodeId) != null) return

        val (localBundle, spkPriv) = x3dh.generateBundleWithPrivateKey(identity)
        val spkHex = localBundle.signedPreKeyPublic.toHex()
        pendingSpkPrivate[spkHex] = spkPriv

        val initMsg = X3dhInitMessage(
            ephemeralPublic      = hs.ephemeralPublic,
            senderIdentityPublic = hs.senderIdentityPublic ?: return
        )
        val session = runCatching {
            x3dh.initAsRecipient(
                identity                  = identity,
                initMessage               = initMsg,
                localBundle               = localBundle,
                localSignedPreKeyPrivate  = spkPriv,
                localOneTimePreKeyPrivate = spkPriv
            )
        }.getOrNull() ?: return

        pendingSpkPrivate.remove(spkHex)
        sessions.save(session)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private suspend fun sendEncrypted(
        connection: MeshConnection,
        session: RatchetSession,
        message: SyncMessage
    ) {
        val (nextSession, ratchetMsg) = ratchet.encrypt(
            session,
            Json.encodeToString(message).toByteArray()
        )
        sessions.save(nextSession)
        val wire = WireMessage(
            dhPublicKey   = ratchetMsg.header.dhPublicKey,
            prevSendCount = ratchetMsg.header.prevSendCount,
            messageNumber = ratchetMsg.header.messageNumber,
            ciphertext    = ratchetMsg.ciphertext
        )
        connection.send(Json.encodeToString<WireFrame>(WireFrame.Message(wire)).toByteArray())
    }
}