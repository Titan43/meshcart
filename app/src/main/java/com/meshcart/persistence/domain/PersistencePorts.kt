package com.meshcart.persistence.domain

import com.meshcart.identity.mnemonic.MnemonicPhrase
import com.meshcart.sync.domain.CrdtState
import com.meshcart.sync.domain.ListId
import com.meshcart.sync.domain.VectorClock

interface IdentityStoragePort {
    fun load(): MnemonicPhrase?
    fun save(phrase: MnemonicPhrase)
    fun clear()
}

interface ListStoragePort {
    fun load(listId: ListId): CrdtState?
    fun save(listId: ListId, state: CrdtState)
    fun loadAll(): List<CrdtState>
    fun delete(listId: ListId)
    fun allClocks(): Map<String, VectorClock>
}