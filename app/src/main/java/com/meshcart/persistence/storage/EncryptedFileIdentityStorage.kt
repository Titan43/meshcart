package com.meshcart.persistence.storage

import android.content.Context
import com.meshcart.identity.mnemonic.MnemonicPhrase
import com.meshcart.persistence.domain.IdentityStoragePort
import java.io.File

class EncryptedFileIdentityStorage(context: Context) : IdentityStoragePort {

    private val file = File(context.filesDir, "identity.dat")
    private val crypto = KeystoreEncryption("meshcart_identity_key")

    override fun load(): MnemonicPhrase? {
        if (!file.exists()) return null
        return runCatching { MnemonicPhrase(String(crypto.decrypt(file.readBytes()))) }.getOrNull()
    }

    override fun save(phrase: MnemonicPhrase) {
        file.writeBytes(crypto.encrypt(phrase.value.toByteArray()))
    }

    override fun clear() = file.delete().let {}
}