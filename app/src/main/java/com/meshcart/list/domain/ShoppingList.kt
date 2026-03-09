package com.meshcart.list.domain

import com.meshcart.identity.domain.NodeId
import com.meshcart.sync.domain.ListId

data class ShoppingList(
    val id: ListId,
    val name: String,
    val ownerId: NodeId,
    val members: Set<NodeId>
) {
    fun isOwner(nodeId: NodeId) = nodeId == ownerId
    fun isMember(nodeId: NodeId) = nodeId in members || isOwner(nodeId)
    fun withMember(nodeId: NodeId) = copy(members = members + nodeId)
    fun withoutMember(nodeId: NodeId) = copy(members = members - nodeId)
    fun renamed(newName: String) = copy(name = newName)
}

class ListAccessDeniedException(message: String) : Exception(message)