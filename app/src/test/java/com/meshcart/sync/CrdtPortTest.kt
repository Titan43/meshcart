package com.meshcart.sync

import com.meshcart.identity.domain.NodeId
import com.meshcart.sync.crdt.OrSetCrdtAdapter
import com.meshcart.sync.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CrdtPortTest {

    private lateinit var crdt: CrdtPort
    private val listId = ListId("list-1")
    private val nodeA = NodeId("a".repeat(64))
    private val nodeB = NodeId("b".repeat(64))

    @Before
    fun setUp() { crdt = OrSetCrdtAdapter() }

    @Test
    fun `upsert adds item to state`() {
        val item = item("i1", "Milk", nodeA, 1L)
        val state = crdt.apply(listId, Operation.Upsert(item, VectorClock().tick(nodeA)))
        assertTrue(state.items().any { it.id == item.id })
    }

    @Test
    fun `remove produces tombstone hiding the item`() {
        val item = item("i1", "Milk", nodeA, 1L)
        val clock = VectorClock().tick(nodeA)
        crdt.apply(listId, Operation.Upsert(item, clock))
        val state = crdt.apply(listId, Operation.Remove(item.id, clock, nodeA))
        assertFalse(state.items().any { it.id == item.id })
    }

    @Test
    fun `concurrent upserts merge without data loss`() {
        val itemA = item("i1", "Milk", nodeA, 1L)
        val itemB = item("i2", "Eggs", nodeB, 2L)
        val stateA = CrdtState(listId, mapOf(itemA.id to itemA))
        val stateB = CrdtState(listId, mapOf(itemB.id to itemB))
        val merged = crdt.merge(stateA, stateB)
        assertEquals(2, merged.items().size)
    }

    @Test
    fun `later timestamp wins on same item conflict`() {
        val older = item("i1", "Milk", nodeA, 1L)
        val newer = item("i1", "Oat Milk", nodeB, 2L)
        val stateA = CrdtState(listId, mapOf(older.id to older))
        val stateB = CrdtState(listId, mapOf(newer.id to newer))
        val merged = crdt.merge(stateA, stateB)
        assertEquals("Oat Milk", merged.items().first { it.id.value == "i1" }.name)
    }

    @Test
    fun `delta returns only operations peer has not seen`() {
        val item = item("i1", "Milk", nodeA, 1L)
        val clock = VectorClock().tick(nodeA)
        val state = crdt.apply(listId, Operation.Upsert(item, clock))
        val delta = crdt.delta(state, clock)
        assertTrue(delta.isEmpty())
    }

    @Test
    fun `delta returns operations peer is missing`() {
        val item = item("i1", "Milk", nodeA, 1L)
        val clock = VectorClock().tick(nodeA)
        val state = crdt.apply(listId, Operation.Upsert(item, clock))
        val delta = crdt.delta(state, VectorClock())
        assertFalse(delta.isEmpty())
    }

    @Test
    fun `vector clock merge takes max of each entry`() {
        val a = VectorClock(mapOf("x" to 3L, "y" to 1L))
        val b = VectorClock(mapOf("x" to 1L, "y" to 5L))
        val merged = a.merge(b)
        assertEquals(3L, merged.entries["x"])
        assertEquals(5L, merged.entries["y"])
    }

    private fun item(id: String, name: String, author: NodeId, ts: Long) =
        ShoppingItem(ItemId(id), name, false, ts, author)
}