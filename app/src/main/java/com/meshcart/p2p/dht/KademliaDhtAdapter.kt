package com.meshcart.p2p.dht

import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId
import com.meshcart.p2p.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KademliaDhtAdapter(
    private val transport: TransportPort
) : DhtPort {

    private val mutex = Mutex()
    private val table = mutableMapOf<NodeId, PeerAddress>()

    private val bootstrapAddresses = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )

    override suspend fun bootstrap() {
        // STUN servers are stateless — they only assist ICE candidate gathering.
        // Bootstrap here means: attempt to reach known peers that were previously
        // persisted or shared via QR, and seed the routing table from them.
        // The STUN servers are configured in WebRtcTransportAdapter's ICE config.
    }

    override suspend fun announce(identity: Identity) {
        // Broadcast our PeerAddress to all connected peers so they can update
        // their routing tables. Called after bootstrap completes.
    }

    override suspend fun lookup(nodeId: NodeId): PeerAddress? = mutex.withLock {
        table[nodeId]
    }

    suspend fun store(address: PeerAddress) = mutex.withLock {
        table[address.nodeId] = address
    }

    suspend fun remove(nodeId: NodeId) = mutex.withLock {
        table.remove(nodeId)
    }

    fun knownPeers(): Set<NodeId> = table.keys.toSet()
}