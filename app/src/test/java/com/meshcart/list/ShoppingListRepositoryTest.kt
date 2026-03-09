package com.meshcart.list

import com.meshcart.identity.domain.NodeId
import com.meshcart.list.domain.*
import com.meshcart.sync.domain.ListId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ShoppingListRepositoryTest {

    private lateinit var repository: ShoppingListRepository
    private val alice = NodeId("a".repeat(64))
    private val bob = NodeId("b".repeat(64))

    @Before
    fun setUp() {
        repository = DefaultShoppingListRepository(InMemoryShoppingListStorage())
    }

    @Test
    fun `create produces list owned by creator`() {
        val list = repository.create("Groceries", alice)
        assertEquals("Groceries", list.name)
        assertTrue(list.isOwner(alice))
    }

    @Test
    fun `owner can add member`() {
        val list = repository.create("Groceries", alice)
        val updated = repository.addMember(list.id, bob, alice)
        assertTrue(updated.isMember(bob))
    }

    @Test(expected = ListAccessDeniedException::class)
    fun `non-owner cannot add member`() {
        val list = repository.create("Groceries", alice)
        repository.addMember(list.id, NodeId("c".repeat(64)), bob)
    }

    @Test
    fun `owner can rename list`() {
        val list = repository.create("Groceries", alice)
        val renamed = repository.rename(list.id, "Weekly Shop", alice)
        assertEquals("Weekly Shop", renamed.name)
    }

    @Test(expected = ListAccessDeniedException::class)
    fun `non-member cannot rename list`() {
        val list = repository.create("Groceries", alice)
        repository.rename(list.id, "Hacked", bob)
    }

    @Test
    fun `member can remove themselves`() {
        val list = repository.create("Groceries", alice)
        val withBob = repository.addMember(list.id, bob, alice)
        val withoutBob = repository.removeMember(withBob.id, bob, bob)
        assertFalse(withoutBob.isMember(bob))
    }

    @Test(expected = ListAccessDeniedException::class)
    fun `member cannot remove other members`() {
        val carol = NodeId("c".repeat(64))
        val list = repository.create("Groceries", alice)
        val withBoth = repository.addMember(list.id, bob, alice)
            .let { repository.addMember(list.id, carol, alice) }
        repository.removeMember(withBoth.id, carol, bob)
    }

    @Test(expected = ListAccessDeniedException::class)
    fun `only owner can delete list`() {
        val list = repository.create("Groceries", alice)
        repository.addMember(list.id, bob, alice)
        repository.delete(list.id, bob)
    }
}

private class InMemoryShoppingListStorage : ShoppingListStoragePort {
    private val store = mutableMapOf<ListId, ShoppingList>()
    override fun load(listId: ListId) = store[listId]
    override fun save(list: ShoppingList) { store[list.id] = list }
    override fun loadAll() = store.values.toList()
    override fun delete(listId: ListId) { store.remove(listId) }
}