package com.meshcart.p2p.domain

import com.meshcart.identity.domain.NodeId
import kotlinx.coroutines.flow.Flow

interface MeshConnection {
    val remoteNodeId: NodeId
    val state: Flow<ConnectionState>
    val incoming: Flow<ByteArray>
    suspend fun send(data: ByteArray)
    fun close()
}

/** Out-of-band signalling needed for ICE restart. */
data class IceRestartOffer(val offerSdp: String)
data class IceRestartAnswer(val answerSdp: String)

interface RenegotiationPort {
    /** Emits when this side has generated a new ICE restart offer that must be sent to the peer. */
    val outboundRestartOffer: kotlinx.coroutines.flow.Flow<IceRestartOffer>
    /** Emits when this side has generated a new ICE restart answer that must be sent to the peer. */
    val outboundRestartAnswer: kotlinx.coroutines.flow.Flow<IceRestartAnswer>
    /** Called when the peer sends us their restart offer. */
    suspend fun applyRestartOffer(offer: IceRestartOffer)
    /** Called when the peer sends us their restart answer. */
    fun applyRestartAnswer(answer: IceRestartAnswer)
}