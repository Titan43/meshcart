package com.meshcart.list.storage

import android.annotation.SuppressLint
import android.content.Context
import com.meshcart.identity.domain.NodeId
import com.meshcart.list.domain.ShoppingList
import com.meshcart.list.domain.ShoppingListStoragePort
import com.meshcart.persistence.storage.KeystoreEncryption
import com.meshcart.sync.domain.ListId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@SuppressLint("UnsafeOptInUsageError")
@Serializable
private data class ShoppingListDto(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<String>
)

private fun ShoppingList.toDto() = ShoppingListDto(
    id = id.value,
    name = name,
    ownerId = ownerId.value,
    members = members.map { it.value }
)

private fun ShoppingListDto.toDomain() = ShoppingList(
    id = ListId(id),
    name = name,
    ownerId = NodeId(ownerId),
    members = members.map { NodeId(it) }.toSet()
)

class EncryptedFileShoppingListStorage(context: Context) : ShoppingListStoragePort {

    private val dir = File(context.filesDir, "metadata").also { it.mkdirs() }
    private val crypto = KeystoreEncryption("meshcart_lists_key")

    override fun load(listId: ListId): ShoppingList? {
        val file = fileFor(listId)
        if (!file.exists()) return null
        return runCatching {
            Json.decodeFromString<ShoppingListDto>(String(crypto.decrypt(file.readBytes()))).toDomain()
        }.getOrNull()
    }

    override fun save(list: ShoppingList) {
        fileFor(list.id).writeBytes(crypto.encrypt(Json.encodeToString(list.toDto()).toByteArray()))
    }

    override fun loadAll(): List<ShoppingList> =
        dir.listFiles()?.mapNotNull { file ->
            runCatching {
                Json.decodeFromString<ShoppingListDto>(String(crypto.decrypt(file.readBytes()))).toDomain()
            }.getOrNull()
        } ?: emptyList()

    override fun delete(listId: ListId) = fileFor(listId).delete().let {}

    private fun fileFor(listId: ListId) = File(dir, "${listId.value}.meta")
}