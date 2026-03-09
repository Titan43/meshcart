package com.meshcart.sync.access

import com.meshcart.identity.crypto.Signature
import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId
import com.meshcart.sync.domain.ListAccessPort
import com.meshcart.sync.domain.ListId
import com.meshcart.sync.domain.SyncMessage

class SignatureListAccessAdapter(
    private val localIdentity: Identity
) : ListAccessPort {

    private val invited = mutableMapOf<ListId, MutableSet<NodeId>>()

    override fun isInvited(listId: ListId, nodeId: NodeId): Boolean =
        invited[listId]?.contains(nodeId) == true ||
                nodeId == localIdentity.nodeId

    override fun invite(listId: ListId, invitee: NodeId, identity: Identity): SyncMessage.Invite {
        val payload = invitePayload(listId, identity.nodeId, invitee)
        val signature = identity.sign(payload)
        invited.getOrPut(listId) { mutableSetOf() }.add(invitee)
        return SyncMessage.Invite(
            listId = listId,
            inviterNodeId = identity.nodeId,
            inviteeNodeId = invitee,
            signature = signature.bytes
        )
    }

    override fun verifyInvite(invite: SyncMessage.Invite): Boolean {
        val payload = invitePayload(invite.listId, invite.inviterNodeId, invite.inviteeNodeId)
        val valid = localIdentity.verify(payload, Signature(invite.signature))
        if (valid) invited.getOrPut(invite.listId) { mutableSetOf() }.add(invite.inviteeNodeId)
        return valid
    }

    private fun invitePayload(listId: ListId, inviter: NodeId, invitee: NodeId): ByteArray =
        "invite:${listId.value}:${inviter.value}:${invitee.value}".toByteArray()
}