package com.meshcart.sync.domain

data class CrdtState(
    val listId: ListId,
    val items: Map<ItemId, ShoppingItem> = emptyMap(),
    val tombstones: Map<ItemId, VectorClock> = emptyMap(),
    val clock: VectorClock = VectorClock()
) {
    fun items(): List<ShoppingItem> = items.values
        .filter { item -> tombstones[item.id]?.dominates(clock) != true }
        .sortedBy { it.timestamp }
}