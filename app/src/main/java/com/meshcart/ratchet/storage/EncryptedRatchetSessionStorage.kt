package com.meshcart.ratchet.storage

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.meshcart.identity.domain.NodeId
import com.meshcart.ratchet.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RatchetSessionDto(
    val peerId: String,
    val rootKey: ByteArray,
    val sendChainKey: ByteArray,
    val recvChainKey: ByteArray,
    val sendRatchetPrivate: ByteArray,
    val sendRatchetPublic: ByteArray,
    val recvRatchetPublic: ByteArray?,
    val sendCount: Int,
    val recvCount: Int,
    val prevSendCount: Int,
    val skippedKeys: Map<String, ByteArray>
)

private fun RatchetSession.toDto() = RatchetSessionDto(
    peerId = peerId.value,
    rootKey = rootKey.bytes,
    sendChainKey = sendChainKey.bytes,
    recvChainKey = recvChainKey.bytes,
    sendRatchetPrivate = sendRatchetPrivate,
    sendRatchetPublic = sendRatchetPublic,
    recvRatchetPublic = recvRatchetPublic,
    sendCount = sendCount,
    recvCount = recvCount,
    prevSendCount = prevSendCount,
    skippedKeys = skippedKeys
)

private fun RatchetSessionDto.toDomain() = RatchetSession(
    peerId = NodeId(peerId),
    rootKey = RootKey(rootKey),
    sendChainKey = ChainKey(sendChainKey),
    recvChainKey = ChainKey(recvChainKey),
    sendRatchetPrivate = sendRatchetPrivate,
    sendRatchetPublic = sendRatchetPublic,
    recvRatchetPublic = recvRatchetPublic,
    sendCount = sendCount,
    recvCount = recvCount,
    prevSendCount = prevSendCount,
    skippedKeys = skippedKeys
)

class EncryptedRatchetSessionStorage(context: Context) : RatchetSessionStoragePort {

    private val dir = File(context.filesDir, "sessions").also { it.mkdirs() }
    private val keyAlias = "meshcart_storage_key"
    private val keystore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    override fun load(peerId: NodeId): RatchetSession? {
        val file = File(dir, "${peerId.value}.session")
        if (!file.exists()) return null
        return runCatching {
            Json.decodeFromString<RatchetSessionDto>(String(decrypt(file.readBytes()))).toDomain()
        }.getOrNull()
    }

    override fun save(session: RatchetSession) {
        File(dir, "${session.peerId.value}.session")
            .writeBytes(encrypt(Json.encodeToString(session.toDto()).toByteArray()))
    }

    override fun delete(peerId: NodeId) {
        File(dir, "${peerId.value}.session").delete()
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size))
    }

    private fun secretKey(): SecretKey {
        keystore.getKey(keyAlias, null)?.let { return it as SecretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .also {
                it.init(
                    KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }.generateKey()
    }
}