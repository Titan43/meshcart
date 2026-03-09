package com.meshcart.list.domain

import com.meshcart.sync.domain.ListId

interface ShoppingListStoragePort {
    fun load(listId: ListId): ShoppingList?
    fun save(list: ShoppingList)
    fun loadAll(): List<ShoppingList>
    fun delete(listId: ListId)
}