package com.meshcart.identity.domain

import com.meshcart.identity.mnemonic.MnemonicPhrase

interface IdentityRepository {
    fun create(): Identity
    fun restore(mnemonic: MnemonicPhrase): Identity
}