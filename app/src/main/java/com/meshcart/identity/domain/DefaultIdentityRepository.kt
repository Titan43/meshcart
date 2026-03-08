package com.meshcart.identity.domain

import com.meshcart.identity.crypto.KeyAgreementPort
import com.meshcart.identity.crypto.SignerPort
import com.meshcart.identity.mnemonic.MnemonicPhrase
import com.meshcart.identity.mnemonic.MnemonicPort

class DefaultIdentityRepository(
    private val mnemonicPort: MnemonicPort,
    private val signer: SignerPort,
    private val keyAgreement: KeyAgreementPort
) : IdentityRepository {

    override fun create() = build(mnemonicPort.generate())

    override fun restore(mnemonic: MnemonicPhrase): Identity {
        if (!mnemonicPort.isValid(mnemonic)) throw InvalidMnemonicException("Invalid BIP39 phrase")
        return build(mnemonic)
    }

    private fun build(phrase: MnemonicPhrase): Identity {
        val seed = mnemonicPort.toSeed(phrase)
        val signingPair = signer.deriveKeyPair(seed.bytes)
        val encryptionPair = keyAgreement.deriveKeyPair(seed.bytes)
        return Identity(
            mnemonic = phrase,
            nodeId = NodeId(signingPair.public.bytes.toHex()),
            signingPublicKey = signingPair.public,
            encryptionPublicKey = encryptionPair.public,
            signingPrivateKey = signingPair.private,
            encryptionPrivateKey = encryptionPair.private,
            signer = signer,
            keyAgreement = keyAgreement
        )
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}