package com.meshcart.identity.domain

import com.meshcart.identity.crypto.*
import com.meshcart.identity.mnemonic.MnemonicPhrase

class Identity internal constructor(
    val mnemonic: MnemonicPhrase,
    val nodeId: NodeId,
    val signingPublicKey: SigningPublicKey,
    val encryptionPublicKey: EncryptionPublicKey,
    private val signingPrivateKey: SigningPrivateKey,
    private val encryptionPrivateKey: EncryptionPrivateKey,
    private val signer: SignerPort,
    private val keyAgreement: KeyAgreementPort
) {
    fun sign(message: ByteArray): Signature = signer.sign(signingPrivateKey, message)
    fun verify(message: ByteArray, signature: Signature): Boolean = signer.verify(signingPublicKey, message, signature)
    fun sharedSecretWith(peerPublicKey: EncryptionPublicKey): SharedSecret = keyAgreement.sharedSecret(encryptionPrivateKey, peerPublicKey)

    override fun equals(other: Any?) = other is Identity && nodeId == other.nodeId
    override fun hashCode() = nodeId.hashCode()
    override fun toString() = "Identity(nodeId=$nodeId)"
}