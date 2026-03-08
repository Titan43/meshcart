package com.meshcart.identity.mnemonic

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import cash.z.ecc.android.bip39.toSeed
import com.meshcart.identity.domain.InvalidMnemonicException

class Bip39MnemonicAdapter : MnemonicPort {

    override fun generate() = MnemonicCode(WordCount.COUNT_24).use { code ->
        MnemonicPhrase(code.joinToString(" "))
    }

    override fun isValid(phrase: MnemonicPhrase) = phrase.value.isNotBlank() && runCatching {
        MnemonicCode(phrase.value).validate()
    }.isSuccess

    override fun toSeed(phrase: MnemonicPhrase, passphrase: String) = try {
        MnemonicSeed(MnemonicCode(phrase.value).toSeed(passphrase.toCharArray()))
    } catch (e: Exception) {
        throw InvalidMnemonicException(e.message ?: "Invalid mnemonic", e)
    }
}