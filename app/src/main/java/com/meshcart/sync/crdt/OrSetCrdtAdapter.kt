package com.meshcart.sync.crdt

import com.meshcart.identity.domain.NodeId
import com.meshcart.sync.domain.*

class OrSetCrdtAdapter : CrdtPort {

    private val states = mutableMapOf<ListId, CrdtState>()

    override fun apply(listId: ListId, operation: Operation): CrdtState {
        val current = states[listId] ?: CrdtState(listId)
        val next = when (operation) {
            is Operation.Upsert -> {
                val existing = current.items[operation.item.id]
                if (existing != null && existing.timestamp >= operation.item.timestamp) current
                else current.copy(
                    items = current.items + (operation.item.id to operation.item),
                    clock = current.clock.merge(operation.clock)
                )
            }
            is Operation.Remove -> current.copy(
                tombstones = current.tombstones + (operation.itemId to operation.clock),
                clock = current.clock.merge(operation.clock)
            )
        }
        states[listId] = next
        return next
    }

    override fun merge(local: CrdtState, remote: CrdtState): CrdtState {
        val mergedItems = (local.items.keys + remote.items.keys).associateWith { id ->
            val l = local.items[id]
            val r = remote.items[id]
            when {
                l == null -> r!!
                r == null -> l
                else -> if (r.timestamp > l.timestamp) r else l
            }
        }
        val mergedTombstones = (local.tombstones.keys + remote.tombstones.keys).associateWith { id ->
            val l = local.tombstones[id]
            val r = remote.tombstones[id]
            when {
                l == null -> r!!
                r == null -> l
                else -> l.merge(r)
            }
        }
        return local.copy(
            items = mergedItems,
            tombstones = mergedTombstones,
            clock = local.clock.merge(remote.clock)
        )
    }

    override fun delta(local: CrdtState, peerClock: VectorClock): List<Operation> {
        val ops = mutableListOf<Operation>()
        local.items.values.forEach { item ->
            val itemClock = VectorClock(mapOf(item.authorId.value to item.timestamp))
            if (!peerClock.dominates(itemClock)) {
                ops += Operation.Upsert(item, itemClock)
            }
        }
        local.tombstones.forEach { (itemId, clock) ->
            if (!peerClock.dominates(clock)) {
                val authorId = clock.entries.keys.firstOrNull()?.let { NodeId(it) } ?: return@forEach
                ops += Operation.Remove(itemId, clock, authorId)
            }
        }
        return ops
    }

    override fun currentClock(listId: ListId): VectorClock =
        states[listId]?.clock ?: VectorClock()
}