package com.meshcart.sync.domain

import com.meshcart.identity.domain.Identity
import com.meshcart.p2p.domain.MeshConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SyncEngine(
    private val identity: Identity,
    private val sync: SyncPort,
    private val crdt: CrdtPort,
    private val access: ListAccessPort,
    private val state: SyncStatePort,
    private val scope: CoroutineScope
) {
    fun onPeerConnected(connection: MeshConnection) {
        scope.launch { sync.send(connection, SyncMessage.Hello(identity.nodeId, state.allClocks())) }
        sync.receive(connection).onEach { message -> handle(connection, message) }.launchIn(scope)
    }

    fun onLocalChange(listId: ListId, operation: Operation) {
        state.saveState(listId, crdt.apply(listId, operation))
    }

    suspend fun sendInvite(connection: MeshConnection, listId: ListId, invitee: com.meshcart.identity.domain.NodeId) {
        val invite = access.invite(listId, invitee, identity)
        sync.send(connection, invite)
    }

    private suspend fun handle(connection: MeshConnection, message: SyncMessage) {
        when (message) {
            is SyncMessage.Hello -> onHello(connection, message)
            is SyncMessage.Delta -> onDelta(message)
            is SyncMessage.Ack   -> Unit
            is SyncMessage.Invite -> onInvite(connection, message)
        }
    }

    private suspend fun onHello(connection: MeshConnection, hello: SyncMessage.Hello) {
        hello.clocks.forEach { (listIdValue, peerClock) ->
            val listId = ListId(listIdValue)
            if (!access.isInvited(listId, hello.senderId)) return@forEach
            val localState = state.getState(listId) ?: return@forEach
            val ops = crdt.delta(localState, peerClock)
            if (ops.isNotEmpty()) sync.send(connection, SyncMessage.Delta(listId, ops))
        }
    }

    private fun onDelta(delta: SyncMessage.Delta) {
        if (!access.isInvited(delta.listId, identity.nodeId)) return
        var current = state.getState(delta.listId) ?: CrdtState(delta.listId)
        delta.operations.forEach { op -> current = crdt.apply(delta.listId, op) }
        state.saveState(delta.listId, current)
    }

    private suspend fun onInvite(connection: MeshConnection, invite: SyncMessage.Invite) {
        if (!access.verifyInvite(invite)) return
        val listId = invite.listId
        val localState = state.getState(listId)
        if (localState != null) {
            sync.send(connection, SyncMessage.Delta(listId, crdt.delta(localState, VectorClock())))
        }
    }
}