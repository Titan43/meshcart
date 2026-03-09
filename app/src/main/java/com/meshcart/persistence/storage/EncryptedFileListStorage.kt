package com.meshcart.persistence.storage

import android.annotation.SuppressLint
import android.content.Context
import com.meshcart.identity.domain.NodeId
import com.meshcart.persistence.domain.ListStoragePort
import com.meshcart.sync.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class ShoppingItemDto(
    val id: String,
    val name: String,
    val checked: Boolean,
    val timestamp: Long,
    val authorId: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class CrdtStateDto(
    val listId: String,
    val items: Map<String, ShoppingItemDto>,
    val tombstones: Map<String, Map<String, Long>>,
    val clock: Map<String, Long>
)

private fun CrdtState.toDto() = CrdtStateDto(
    listId = listId.value,
    items = items.map { (k, v) ->
        k.value to ShoppingItemDto(v.id.value, v.name, v.checked, v.timestamp, v.authorId.value)
    }.toMap(),
    tombstones = tombstones.map { (k, v) -> k.value to v.entries }.toMap(),
    clock = clock.entries
)

private fun CrdtStateDto.toDomain() = CrdtState(
    listId = ListId(listId),
    items = items.map { (_, v) ->
        ItemId(v.id) to ShoppingItem(ItemId(v.id), v.name, v.checked, v.timestamp, NodeId(v.authorId))
    }.toMap(),
    tombstones = tombstones.map { (k, v) -> ItemId(k) to VectorClock(v) }.toMap(),
    clock = VectorClock(clock)
)

class EncryptedFileListStorage(context: Context) : ListStoragePort {

    private val dir = File(context.filesDir, "lists").also { it.mkdirs() }
    private val crypto = KeystoreEncryption("meshcart_lists_key")
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(listId: ListId): CrdtState? {
        val file = fileFor(listId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<CrdtStateDto>(String(crypto.decrypt(file.readBytes()))).toDomain()
        }.getOrNull()
    }

    override fun save(listId: ListId, state: CrdtState) {
        fileFor(listId).writeBytes(crypto.encrypt(json.encodeToString(state.toDto()).toByteArray()))
    }

    override fun loadAll(): List<CrdtState> =
        dir.listFiles()?.mapNotNull { file ->
            runCatching {
                json.decodeFromString<CrdtStateDto>(String(crypto.decrypt(file.readBytes()))).toDomain()
            }.getOrNull()
        } ?: emptyList()

    override fun delete(listId: ListId) = fileFor(listId).delete().let {}

    override fun allClocks(): Map<String, VectorClock> =
        loadAll().associate { it.listId.value to it.clock }

    private fun fileFor(listId: ListId) = File(dir, "${listId.value}.list")
}