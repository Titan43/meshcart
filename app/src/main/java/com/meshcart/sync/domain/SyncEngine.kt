package com.meshcart.sync.domain

import com.meshcart.identity.domain.Identity
import com.meshcart.p2p.domain.MeshConnection
import com.meshcart.p2p.domain.RenegotiationPort
import com.meshcart.p2p.transport.WebRtcMeshConnection
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
        // Start receive() FIRST so no incoming bytes are missed before advertiseBundle()
        sync.receive(connection).onEach { message -> handle(connection, message) }.launchIn(scope)

        scope.launch {
            if (connection is WebRtcMeshConnection) connection.awaitOpen()
            // Step 1: advertise our X3DH bundle — triggers handshake via receive() flow
            (sync as? SyncAdapter)?.advertiseBundle(connection)
            // Step 2: send Hello — SyncAdapter.send() suspends until handshake completes
            sync.send(connection, SyncMessage.Hello(identity.nodeId, state.allClocks()))
        }

        // If this connection supports ICE restart, relay the signalling
        // through the encrypted DataChannel so no extra out-of-band step is needed.
        if (connection is RenegotiationPort) {
            connection.outboundRestartOffer
                .onEach { offer ->
                    runCatching {
                        sync.send(connection, SyncMessage.IceRestartOffer(offer.offerSdp))
                    }
                }.launchIn(scope)

            connection.outboundRestartAnswer
                .onEach { answer ->
                    runCatching {
                        sync.send(connection, SyncMessage.IceRestartAnswer(answer.answerSdp))
                    }
                }.launchIn(scope)
        }
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
            is SyncMessage.Hello          -> onHello(connection, message)
            is SyncMessage.Delta          -> onDelta(message)
            is SyncMessage.Ack            -> Unit
            is SyncMessage.Invite         -> onInvite(connection, message)
            is SyncMessage.IceRestartOffer  -> onIceRestartOffer(connection, message)
            is SyncMessage.IceRestartAnswer -> onIceRestartAnswer(connection, message)
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

    // ICE restart: the peer's network changed, they sent us a new offer
    private suspend fun onIceRestartOffer(connection: MeshConnection, msg: SyncMessage.IceRestartOffer) {
        if (connection is RenegotiationPort) {
            connection.applyRestartOffer(
                com.meshcart.p2p.domain.IceRestartOffer(msg.offerSdp)
            )
        }
    }

    // ICE restart: we sent a restart offer, peer responded with an answer
    private fun onIceRestartAnswer(connection: MeshConnection, msg: SyncMessage.IceRestartAnswer) {
        if (connection is RenegotiationPort) {
            connection.applyRestartAnswer(
                com.meshcart.p2p.domain.IceRestartAnswer(msg.answerSdp)
            )
        }
    }
}