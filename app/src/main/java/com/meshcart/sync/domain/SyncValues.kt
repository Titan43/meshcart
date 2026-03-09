package com.meshcart.sync.domain

import android.annotation.SuppressLint
import com.meshcart.identity.domain.NodeId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline value class ListId(val value: String)
@JvmInline value class ItemId(val value: String)

object NodeIdSerializer : KSerializer<NodeId> {
    override val descriptor = PrimitiveSerialDescriptor("NodeId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: NodeId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = NodeId(decoder.decodeString())
}

object ListIdSerializer : KSerializer<ListId> {
    override val descriptor = PrimitiveSerialDescriptor("ListId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ListId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = ListId(decoder.decodeString())
}

object ItemIdSerializer : KSerializer<ItemId> {
    override val descriptor = PrimitiveSerialDescriptor("ItemId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ItemId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = ItemId(decoder.decodeString())
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ShoppingItem(
    @Serializable(with = ItemIdSerializer::class) val id: ItemId,
    val name: String,
    val checked: Boolean,
    val timestamp: Long,
    @Serializable(with = NodeIdSerializer::class) val authorId: NodeId
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class VectorClock(val entries: Map<String, Long> = emptyMap()) {
    fun tick(nodeId: NodeId): VectorClock =
        copy(entries = entries + (nodeId.value to (entries[nodeId.value] ?: 0L) + 1))

    fun dominates(other: VectorClock): Boolean =
        other.entries.all { (k, v) -> (entries[k] ?: 0L) >= v }

    fun merge(other: VectorClock): VectorClock =
        copy(entries = (entries.keys + other.entries.keys).associateWith { k ->
            maxOf(entries[k] ?: 0L, other.entries[k] ?: 0L)
        })
}