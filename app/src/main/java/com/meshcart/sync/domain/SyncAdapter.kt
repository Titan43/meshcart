package com.meshcart.sync.domain

import com.meshcart.p2p.domain.MeshConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncAdapter : SyncPort {

    override suspend fun send(connection: MeshConnection, message: SyncMessage) {
        connection.send(Json.encodeToString(message).toByteArray())
    }

    override fun receive(connection: MeshConnection): Flow<SyncMessage> =
        connection.incoming.mapNotNull { bytes ->
            runCatching { Json.decodeFromString<SyncMessage>(String(bytes)) }.getOrNull()
        }
}