package com.meshcart.list.domain

import com.meshcart.identity.domain.NodeId
import com.meshcart.persistence.domain.ListStoragePort
import com.meshcart.sync.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

class DefaultShoppingItemRepository(
    private val crdt: CrdtPort,
    private val storage: ListStoragePort,
    private val listRepository: ShoppingListRepository
) : ShoppingItemRepository {

    private val cache = MutableStateFlow<Map<ListId, CrdtState>>(emptyMap())

    init {
        cache.value = storage.loadAll().associateBy { it.listId }
    }

    override fun observe(listId: ListId): Flow<List<ShoppingItem>> =
        cache.map { it[listId]?.items() ?: emptyList() }

    override fun add(listId: ListId, name: String, author: NodeId): ShoppingItem {
        requireMembership(listId, author)
        val item = ShoppingItem(
            id = ItemId(UUID.randomUUID().toString()),
            name = name,
            checked = false,
            timestamp = System.currentTimeMillis(),
            authorId = author
        )
        val clock = VectorClock().tick(author)
        applyAndPersist(listId, Operation.Upsert(item, clock))
        return item
    }

    override fun check(listId: ListId, itemId: ItemId, checked: Boolean, author: NodeId): ShoppingItem {
        requireMembership(listId, author)
        val state = cache.value[listId] ?: throw NoSuchElementException("List $listId not found")
        val existing = state.items[itemId] ?: throw NoSuchElementException("Item $itemId not found")
        val updated = existing.copy(checked = checked, timestamp = System.currentTimeMillis())
        val clock = state.clock.tick(author)
        applyAndPersist(listId, Operation.Upsert(updated, clock))
        return updated
    }

    override fun remove(listId: ListId, itemId: ItemId, author: NodeId) {
        requireMembership(listId, author)
        val state = cache.value[listId] ?: throw NoSuchElementException("List $listId not found")
        val clock = state.clock.tick(author)
        applyAndPersist(listId, Operation.Remove(itemId, clock, author))
    }

    private fun applyAndPersist(listId: ListId, operation: Operation) {
        val next = crdt.apply(listId, operation)
        storage.save(listId, next)
        cache.update { it + (listId to next) }
    }

    private fun requireMembership(listId: ListId, nodeId: NodeId) {
        val list = listRepository.get(listId) ?: throw NoSuchElementException("List $listId not found")
        if (!list.isMember(nodeId)) throw ListAccessDeniedException("$nodeId is not a member of $listId")
    }
}