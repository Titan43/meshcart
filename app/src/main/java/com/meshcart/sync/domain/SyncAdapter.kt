package com.meshcart.sync.domain

import android.annotation.SuppressLint
import com.meshcart.p2p.domain.MeshConnection
import com.meshcart.ratchet.domain.RatchetPort
import com.meshcart.ratchet.domain.RatchetSessionStoragePort
import com.meshcart.ratchet.domain.RatchetHeader
import com.meshcart.ratchet.domain.RatchetMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@SuppressLint("UnsafeOptInUsageError")
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

class SyncAdapter(
    private val ratchet: RatchetPort,
    private val sessions: RatchetSessionStoragePort
) : SyncPort {

    override suspend fun send(connection: MeshConnection, message: SyncMessage) {
        val session = sessions.load(connection.remoteNodeId)
            ?: throw IllegalStateException("No ratchet session for ${connection.remoteNodeId}")
        val (nextSession, ratchetMessage) = ratchet.encrypt(session, Json.encodeToString(message).toByteArray())
        sessions.save(nextSession)
        val wire = WireMessage(
            dhPublicKey = ratchetMessage.header.dhPublicKey,
            prevSendCount = ratchetMessage.header.prevSendCount,
            messageNumber = ratchetMessage.header.messageNumber,
            ciphertext = ratchetMessage.ciphertext
        )
        connection.send(Json.encodeToString(wire).toByteArray())
    }

    override fun receive(connection: MeshConnection): Flow<SyncMessage> =
        connection.incoming.mapNotNull { bytes ->
            val wire = runCatching { Json.decodeFromString<WireMessage>(String(bytes)) }.getOrNull()
                ?: return@mapNotNull null
            val session = sessions.load(connection.remoteNodeId)
                ?: return@mapNotNull null
            val (nextSession, plaintext) = runCatching { ratchet.decrypt(session, wire.toRatchetMessage()) }.getOrNull()
                ?: return@mapNotNull null
            sessions.save(nextSession)
            runCatching { Json.decodeFromString<SyncMessage>(String(plaintext)) }.getOrNull()
        }
}