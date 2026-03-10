package com.meshcart.sync.domain

import android.util.Log
import com.meshcart.identity.domain.Identity
import com.meshcart.p2p.domain.MeshConnection
import com.meshcart.ratchet.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "SyncAdapter"

// ── Wire types ────────────────────────────────────────────────────────────────

@Serializable
private data class WireHandshake(
    val identityPublic:        ByteArray,
    val signedPreKeyPublic:    ByteArray,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyPublic:   ByteArray,
    // null = bundle-only advertisement; non-null = X3DH init message
    val ephemeralPublic:       ByteArray? = null,
    val senderIdentityPublic:  ByteArray? = null
)

@Serializable
private data class WireMessage(
    val dhPublicKey:   ByteArray,
    val prevSendCount: Int,
    val messageNumber: Int,
    val ciphertext:    ByteArray
) {
    fun toRatchetMessage() = RatchetMessage(
        header = RatchetHeader(dhPublicKey, prevSendCount, messageNumber),
        ciphertext = ciphertext
    )
}

@Serializable
private sealed class WireFrame {
    @Serializable @SerialName("hs")  data class Handshake(val payload: WireHandshake) : WireFrame()
    @Serializable @SerialName("msg") data class Message(val payload: WireMessage)     : WireFrame()
}

// ── SyncAdapter ───────────────────────────────────────────────────────────────

class SyncAdapter(
    private val identity: Identity,
    private val x3dh:     X3dhPort,
    private val ratchet:  RatchetPort,
    private val sessions: RatchetSessionStoragePort
) : SyncPort {

    // SPK private keys keyed by SPK public hex
    private val pendingSpkPrivate = mutableMapOf<String, ByteArray>()

    // Per-peer channel that fires Unit when a session is established
    // — used to unblock send() callers waiting for handshake
    private val sessionReady = mutableMapOf<String, Channel<Unit>>()

    private fun sessionChannel(peerId: String) =
        sessionReady.getOrPut(peerId) { Channel(Channel.CONFLATED) }

    /**
     * Step 1 of handshake — called by SyncEngine immediately after peer connects.
     * Sends our bundle advertisement so the peer knows our real X3DH keys.
     */
    suspend fun advertiseBundle(connection: MeshConnection) {
        val (bundle, spkPriv) = x3dh.generateBundleWithPrivateKey(identity)
        pendingSpkPrivate[bundle.signedPreKeyPublic.toHex()] = spkPriv

        val advert = WireHandshake(
            identityPublic        = bundle.identityPublic,
            signedPreKeyPublic    = bundle.signedPreKeyPublic,
            signedPreKeySignature = bundle.signedPreKeySignature,
            oneTimePreKeyPublic   = bundle.oneTimePreKeyPublic
            // ephemeralPublic = null → bundle-only
        )
        Log.d(TAG, "Advertising bundle to ${connection.remoteNodeId.value.take(8)}")
        sendFrame(connection, WireFrame.Handshake(advert))
    }

    /**
     * Send a [SyncMessage]. If no session exists yet, waits up to 30s for
     * the handshake to complete (driven by the receive() flow).
     */
    override suspend fun send(connection: MeshConnection, message: SyncMessage) {
        val peerId = connection.remoteNodeId.value
        if (sessions.load(connection.remoteNodeId) == null) {
            Log.d(TAG, "No session yet for ${peerId.take(8)}, waiting for handshake…")
            withTimeout(30_000) { sessionChannel(peerId).receive() }
        }
        val session = sessions.load(connection.remoteNodeId)
            ?: throw IllegalStateException("Session disappeared for $peerId")
        sendEncrypted(connection, session, message)
    }

    override fun receive(connection: MeshConnection): Flow<SyncMessage> =
        connection.incoming.mapNotNull { bytes ->
            val frame = runCatching {
                Json.decodeFromString<WireFrame>(String(bytes))
            }.getOrNull() ?: return@mapNotNull null

            when (frame) {
                is WireFrame.Handshake -> {
                    // Launch separately so we can send (X3DH init) without deadlocking
                    // the DataChannel receive callback thread
                    CoroutineScope(Dispatchers.IO).launch {
                        handleHandshake(connection, frame.payload)
                    }
                    null
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

    // ── Private: handshake state machine ─────────────────────────────────────

    private suspend fun handleHandshake(connection: MeshConnection, hs: WireHandshake) {
        val peerId = connection.remoteNodeId.value

        if (hs.ephemeralPublic != null) {
            // ── Recipient path: we received a full X3DH init message ──────────
            Log.d(TAG, "Received X3DH init from ${peerId.take(8)}")
            if (sessions.load(connection.remoteNodeId) != null) {
                Log.d(TAG, "Session already exists, ignoring duplicate init")
                return
            }

            // Find the matching SPK private key (we advertised it earlier)
            val spkHex = hs.signedPreKeyPublic.toHex()
            val spkPriv = pendingSpkPrivate[spkHex] ?: run {
                // Shouldn't happen in normal flow, but recover gracefully
                Log.w(TAG, "No pending SPK for $spkHex — generating fresh one")
                val (b, p) = x3dh.generateBundleWithPrivateKey(identity)
                pendingSpkPrivate[b.signedPreKeyPublic.toHex()] = p
                p
            }

            val (localBundle, localSpkPriv) = x3dh.generateBundleWithPrivateKey(identity)
            val initMsg = X3dhInitMessage(
                ephemeralPublic      = hs.ephemeralPublic,
                senderIdentityPublic = hs.senderIdentityPublic ?: run {
                    Log.e(TAG, "Missing senderIdentityPublic in init message")
                    return
                }
            )

            val session = runCatching {
                x3dh.initAsRecipient(
                    identity                  = identity,
                    initMessage               = initMsg,
                    localBundle               = localBundle,
                    localSignedPreKeyPrivate  = localSpkPriv,
                    localOneTimePreKeyPrivate = localSpkPriv
                )
            }.onFailure { Log.e(TAG, "initAsRecipient failed: ${it.message}") }
                .getOrNull() ?: return

            pendingSpkPrivate.remove(spkHex)
            sessions.save(session)
            Log.d(TAG, "✓ Session established as RECIPIENT with ${peerId.take(8)}")
            sessionChannel(peerId).trySend(Unit)

        } else {
            // ── Initiator path: we received a bundle advertisement ────────────
            Log.d(TAG, "Received bundle advert from ${peerId.take(8)}")

            if (sessions.load(connection.remoteNodeId) != null) {
                Log.d(TAG, "Session already exists, ignoring advert")
                return
            }

            // Deterministic tie-break using the bundle's actual identity public key bytes
            // (connection.remoteNodeId is unreliable — both sides currently pass their OWN nodeId)
            val peerKeyHex = hs.identityPublic.joinToString("") { "%02x".format(it) }
            val ourKeyHex  = identity.encryptionPublicKey.bytes.joinToString("") { "%02x".format(it) }
            val weInitiate = ourKeyHex < peerKeyHex
            if (!weInitiate) {
                Log.d(TAG, "We are RESPONDER (our=${ ourKeyHex.take(8)}, peer=${peerKeyHex.take(8)}) — waiting for their X3DH init")
                return
            }

            Log.d(TAG, "We are INITIATOR (our=${ourKeyHex.take(8)}, peer=${peerKeyHex.take(8)}) — performing X3DH")

            val recipientBundle = X3dhBundle(
                identityPublic        = hs.identityPublic,
                signedPreKeyPublic    = hs.signedPreKeyPublic,
                signedPreKeySignature = hs.signedPreKeySignature,
                oneTimePreKeyPublic   = hs.oneTimePreKeyPublic
            )

            val (localBundle, spkPriv) = x3dh.generateBundleWithPrivateKey(identity)
            pendingSpkPrivate[localBundle.signedPreKeyPublic.toHex()] = spkPriv

            val (session, initMsg) = runCatching {
                x3dh.initAsSender(identity, recipientBundle)
            }.onFailure { Log.e(TAG, "initAsSender failed: ${it.message}") }
                .getOrNull() ?: return

            sessions.save(session)

            val initHandshake = WireHandshake(
                identityPublic        = localBundle.identityPublic,
                signedPreKeyPublic    = localBundle.signedPreKeyPublic,
                signedPreKeySignature = localBundle.signedPreKeySignature,
                oneTimePreKeyPublic   = localBundle.oneTimePreKeyPublic,
                ephemeralPublic       = initMsg.ephemeralPublic,
                senderIdentityPublic  = initMsg.senderIdentityPublic
            )
            sendFrame(connection, WireFrame.Handshake(initHandshake))

            Log.d(TAG, "✓ Session established as INITIATOR with ${peerId.take(8)}")
            sessionChannel(peerId).trySend(Unit)
        }
    }

    // ── Private: send helpers ─────────────────────────────────────────────────

    private suspend fun sendFrame(connection: MeshConnection, frame: WireFrame) {
        connection.send(Json.encodeToString(frame).toByteArray())
    }

    private suspend fun sendEncrypted(
        connection: MeshConnection,
        session:    RatchetSession,
        message:    SyncMessage
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
        sendFrame(connection, WireFrame.Message(wire))
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}