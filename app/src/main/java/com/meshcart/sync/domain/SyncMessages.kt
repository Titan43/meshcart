package com.meshcart.sync.domain

import android.annotation.SuppressLint
import com.meshcart.identity.domain.NodeId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SyncMessage {

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable @SerialName("hello")
    data class Hello(
        @Serializable(with = NodeIdSerializer::class) val senderId: NodeId,
        val clocks: Map<String, VectorClock>
    ) : SyncMessage()

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable @SerialName("delta")
    data class Delta(
        @Serializable(with = ListIdSerializer::class) val listId: ListId,
        val operations: List<Operation>
    ) : SyncMessage()

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable @SerialName("ack")
    data class Ack(
        @Serializable(with = ListIdSerializer::class) val listId: ListId,
        val clock: VectorClock
    ) : SyncMessage()

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable @SerialName("invite")
    data class Invite(
        @Serializable(with = ListIdSerializer::class) val listId: ListId,
        @Serializable(with = NodeIdSerializer::class) val inviterNodeId: NodeId,
        @Serializable(with = NodeIdSerializer::class) val inviteeNodeId: NodeId,
        val signature: ByteArray
    ) : SyncMessage()
}

@Serializable
sealed class Operation {

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable @SerialName("upsert")
    data class Upsert(val item: ShoppingItem, val clock: VectorClock) : Operation()

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable @SerialName("remove")
    data class Remove(
        @Serializable(with = ItemIdSerializer::class) val itemId: ItemId,
        val clock: VectorClock,
        @Serializable(with = NodeIdSerializer::class) val authorId: NodeId
    ) : Operation()
}