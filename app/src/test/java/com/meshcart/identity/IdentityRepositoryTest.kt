package com.meshcart.identity

import com.meshcart.identity.crypto.BouncyCastleKeyAgreementAdapter
import com.meshcart.identity.crypto.BouncyCastleSignerAdapter
import com.meshcart.identity.domain.DefaultIdentityRepository
import com.meshcart.identity.domain.IdentityRepository
import com.meshcart.identity.domain.InvalidMnemonicException
import com.meshcart.identity.mnemonic.Bip39MnemonicAdapter
import com.meshcart.identity.mnemonic.MnemonicPhrase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IdentityRepositoryTest {

    private lateinit var repository: IdentityRepository

    @Before
    fun setUp() {
        repository = DefaultIdentityRepository(
            mnemonicPort = Bip39MnemonicAdapter(),
            signer = BouncyCastleSignerAdapter(),
            keyAgreement = BouncyCastleKeyAgreementAdapter()
        )
    }

    @Test
    fun `two created identities are distinct`() {
        assertNotEquals(repository.create().nodeId, repository.create().nodeId)
    }

    @Test
    fun `restoring from mnemonic reproduces the same identity`() {
        val original = repository.create()
        val restored = repository.restore(original.mnemonic)
        assertEquals(original.nodeId, restored.nodeId)
    }

    @Test(expected = InvalidMnemonicException::class)
    fun `restoring invalid mnemonic throws`() {
        repository.restore(MnemonicPhrase("not valid"))
    }

    @Test
    fun `identity can sign and verify its own messages`() {
        val identity = repository.create()
        val sig = identity.sign("hello mesh".toByteArray())
        assertTrue(identity.verify("hello mesh".toByteArray(), sig))
    }

    @Test
    fun `signature from one identity does not verify under another`() {
        val alice = repository.create()
        val bob = repository.create()
        val sig = alice.sign("data".toByteArray())
        assertFalse(bob.verify("data".toByteArray(), sig))
    }

    @Test
    fun `two peers derive the same shared secret`() {
        val alice = repository.create()
        val bob = repository.create()
        assertArrayEquals(
            alice.sharedSecretWith(bob.encryptionPublicKey).bytes,
            bob.sharedSecretWith(alice.encryptionPublicKey).bytes
        )
    }
}