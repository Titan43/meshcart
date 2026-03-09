package com.meshcart.sync.domain

import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId
import com.meshcart.p2p.domain.MeshConnection
import kotlinx.coroutines.flow.Flow

interface SyncPort {
    suspend fun send(connection: MeshConnection, message: SyncMessage)
    fun receive(connection: MeshConnection): Flow<SyncMessage>
}

interface CrdtPort {
    fun apply(listId: ListId, operation: Operation): CrdtState
    fun merge(local: CrdtState, remote: CrdtState): CrdtState
    fun delta(local: CrdtState, peerClock: VectorClock): List<Operation>
    fun currentClock(listId: ListId): VectorClock
}

interface ListAccessPort {
    fun isInvited(listId: ListId, nodeId: NodeId): Boolean
    fun invite(listId: ListId, invitee: NodeId, identity: Identity): SyncMessage.Invite
    fun verifyInvite(invite: SyncMessage.Invite): Boolean
}

interface SyncStatePort {
    fun getState(listId: ListId): CrdtState?
    fun saveState(listId: ListId, state: CrdtState)
    fun allClocks(): Map<String, VectorClock>
}