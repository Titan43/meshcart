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