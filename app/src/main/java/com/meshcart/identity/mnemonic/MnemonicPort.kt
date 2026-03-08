package com.meshcart.identity.mnemonic

import com.meshcart.identity.domain.InvalidMnemonicException

@JvmInline value class MnemonicPhrase(val value: String) { override fun toString() = "MnemonicPhrase[redacted]" }
@JvmInline value class MnemonicSeed(val bytes: ByteArray) { override fun toString() = "MnemonicSeed[redacted]" }

interface MnemonicPort {
    fun generate(): MnemonicPhrase
    fun isValid(phrase: MnemonicPhrase): Boolean
    @Throws(InvalidMnemonicException::class)
    fun toSeed(phrase: MnemonicPhrase, passphrase: String = ""): MnemonicSeed
}