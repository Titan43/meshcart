package com.meshcart.list.domain

import com.meshcart.identity.domain.NodeId
import com.meshcart.sync.domain.ItemId
import com.meshcart.sync.domain.ListId
import com.meshcart.sync.domain.ShoppingItem
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    fun observeAll(): Flow<List<ShoppingList>>
    fun observe(listId: ListId): Flow<ShoppingList?>
    fun get(listId: ListId): ShoppingList?
    fun create(name: String, owner: NodeId): ShoppingList
    fun rename(listId: ListId, name: String, requestor: NodeId): ShoppingList
    fun delete(listId: ListId, requestor: NodeId)
    fun addMember(listId: ListId, member: NodeId, requestor: NodeId): ShoppingList
    fun removeMember(listId: ListId, member: NodeId, requestor: NodeId): ShoppingList
}

interface ShoppingItemRepository {
    fun observe(listId: ListId): Flow<List<ShoppingItem>>
    fun add(listId: ListId, name: String, author: NodeId): ShoppingItem
    fun check(listId: ListId, itemId: ItemId, checked: Boolean, author: NodeId): ShoppingItem
    fun remove(listId: ListId, itemId: ItemId, author: NodeId)
}