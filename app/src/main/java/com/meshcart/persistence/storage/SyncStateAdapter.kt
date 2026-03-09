package com.meshcart.persistence.storage

import com.meshcart.persistence.domain.ListStoragePort
import com.meshcart.sync.domain.*

class SyncStateAdapter(private val storage: ListStoragePort) : SyncStatePort {

    override fun getState(listId: ListId): CrdtState? = storage.load(listId)

    override fun saveState(listId: ListId, state: CrdtState) = storage.save(listId, state)

    override fun allClocks(): Map<String, VectorClock> = storage.allClocks()
}