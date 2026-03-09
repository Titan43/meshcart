package com.meshcart.sync.domain

import com.meshcart.identity.domain.NodeId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SyncMessage {

    @Serializable @SerialName("hello")
    data class Hello(
        @Serializable(with = NodeIdSerializer::class) val senderId: NodeId,
        val clocks: Map<String, VectorClock>
    ) : SyncMessage()

    @Serializable @SerialName("delta")
    data class Delta(
        @Serializable(with = ListIdSerializer::class) val listId: ListId,
        val operations: List<Operation>
    ) : SyncMessage()

    @Serializable @SerialName("ack")
    data class Ack(
        @Serializable(with = ListIdSerializer::class) val listId: ListId,
        val clock: VectorClock
    ) : SyncMessage()

    @Serializable @SerialName("invite")
    data class Invite(
        @Serializable(with = ListIdSerializer::class) val listId: ListId,
        @Serializable(with = NodeIdSerializer::class) val inviterNodeId: NodeId,
        @Serializable(with = NodeIdSerializer::class) val inviteeNodeId: NodeId,
        val signature: ByteArray
    ) : SyncMessage()

    /** ICE restart offer — sent when a peer's network changes. */
    @Serializable @SerialName("ice_offer")
    data class IceRestartOffer(val offerSdp: String) : SyncMessage()

    /** ICE restart answer — sent in response to IceRestartOffer. */
    @Serializable @SerialName("ice_answer")
    data class IceRestartAnswer(val answerSdp: String) : SyncMessage()
}

@Serializable
sealed class Operation {

    @Serializable @SerialName("upsert")
    data class Upsert(val item: ShoppingItem, val clock: VectorClock) : Operation()

    @Serializable @SerialName("remove")
    data class Remove(
        @Serializable(with = ItemIdSerializer::class) val itemId: ItemId,
        val clock: VectorClock,
        @Serializable(with = NodeIdSerializer::class) val authorId: NodeId
    ) : Operation()
}