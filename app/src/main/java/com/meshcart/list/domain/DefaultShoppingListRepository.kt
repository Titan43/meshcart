package com.meshcart.list.domain

import com.meshcart.identity.domain.NodeId
import com.meshcart.sync.domain.ListId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

class DefaultShoppingListRepository(
    private val storage: ShoppingListStoragePort
) : ShoppingListRepository {

    private val cache = MutableStateFlow<Map<ListId, ShoppingList>>(emptyMap())

    init {
        val loaded = storage.loadAll().associateBy { it.id }
        cache.value = loaded
    }

    override fun observeAll(): Flow<List<ShoppingList>> =
        cache.map { it.values.sortedBy { list -> list.name } }

    override fun observe(listId: ListId): Flow<ShoppingList?> =
        cache.map { it[listId] }

    override fun get(listId: ListId): ShoppingList? = cache.value[listId]

    override fun create(name: String, owner: NodeId): ShoppingList {
        val list = ShoppingList(
            id = ListId(UUID.randomUUID().toString()),
            name = name,
            ownerId = owner,
            members = emptySet()
        )
        persist(list)
        return list
    }

    override fun rename(listId: ListId, name: String, requestor: NodeId): ShoppingList {
        val list = requireAccess(listId, requestor)
        return persist(list.renamed(name))
    }

    override fun delete(listId: ListId, requestor: NodeId) {
        val list = requireAccess(listId, requestor)
        if (!list.isOwner(requestor)) throw ListAccessDeniedException("Only the owner can delete a list")
        storage.delete(listId)
        cache.update { it - listId }
    }

    override fun addMember(listId: ListId, member: NodeId, requestor: NodeId): ShoppingList {
        val list = requireAccess(listId, requestor)
        if (!list.isOwner(requestor)) throw ListAccessDeniedException("Only the owner can invite members")
        return persist(list.withMember(member))
    }

    override fun removeMember(listId: ListId, member: NodeId, requestor: NodeId): ShoppingList {
        val list = requireAccess(listId, requestor)
        if (!list.isOwner(requestor) && member != requestor)
            throw ListAccessDeniedException("Only the owner can remove other members")
        return persist(list.withoutMember(member))
    }

    private fun requireAccess(listId: ListId, nodeId: NodeId): ShoppingList {
        val list = cache.value[listId] ?: throw NoSuchElementException("List $listId not found")
        if (!list.isMember(nodeId)) throw ListAccessDeniedException("$nodeId is not a member of $listId")
        return list
    }

    private fun persist(list: ShoppingList): ShoppingList {
        storage.save(list)
        cache.update { it + (list.id to list) }
        return list
    }
}