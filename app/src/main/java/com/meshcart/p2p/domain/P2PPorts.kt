package com.meshcart.p2p.domain

import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId
import kotlinx.coroutines.flow.Flow

interface TransportPort {
    val incoming: Flow<MeshConnection>
    suspend fun connect(address: PeerAddress, localIdentity: Identity): MeshConnection
    suspend fun listen(localIdentity: Identity)
    fun stop()
}

interface DhtPort {
    suspend fun announce(identity: Identity)
    suspend fun lookup(nodeId: NodeId): PeerAddress?
    suspend fun bootstrap()
}

interface QrPort {
    fun encode(address: PeerAddress): String
    fun decode(payload: String): PeerAddress
}

interface PeerDiscoveryPort {
    val discovered: Flow<PeerAddress>
    suspend fun start(identity: Identity)
    fun stop()
}