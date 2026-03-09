package com.meshcart.ratchet

import com.meshcart.identity.domain.NodeId
import com.meshcart.ratchet.crypto.DoubleRatchetAdapter
import com.meshcart.ratchet.crypto.newX25519KeyPair
import com.meshcart.ratchet.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import kotlin.experimental.xor

class DoubleRatchetTest {

    private lateinit var ratchet: DoubleRatchetAdapter
    private lateinit var alice: RatchetSession
    private lateinit var bob: RatchetSession

    @Before
    fun setUp() {
        ratchet = DoubleRatchetAdapter()
        val sharedSecret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val (alicePriv, alicePub) = newX25519KeyPair()
        val (bobPriv, bobPub) = newX25519KeyPair()

        alice = RatchetSession(
            peerId = NodeId("b".repeat(64)),
            rootKey = RootKey(sharedSecret),
            sendChainKey = ChainKey(sharedSecret),
            recvChainKey = ChainKey(sharedSecret),
            sendRatchetPrivate = alicePriv,
            sendRatchetPublic = alicePub,
            recvRatchetPublic = bobPub
        )
        bob = RatchetSession(
            peerId = NodeId("a".repeat(64)),
            rootKey = RootKey(sharedSecret),
            sendChainKey = ChainKey(sharedSecret),
            recvChainKey = ChainKey(sharedSecret),
            sendRatchetPrivate = bobPriv,
            sendRatchetPublic = bobPub,
            recvRatchetPublic = alicePub
        )
    }

    @Test
    fun `alice sends bob receives`() {
        val (_, msg) = ratchet.encrypt(alice, "hello".toByteArray())
        val (_, plaintext) = ratchet.decrypt(bob, msg)
        assertEquals("hello", String(plaintext))
    }

    @Test
    fun `sequential messages all decrypt correctly`() {
        var a = alice
        var b = bob
        listOf("one", "two", "three").forEach { text ->
            val (nextA, msg) = ratchet.encrypt(a, text.toByteArray())
            val (nextB, plaintext) = ratchet.decrypt(b, msg)
            assertEquals(text, String(plaintext))
            a = nextA
            b = nextB
        }
    }

    @Test
    fun `bidirectional exchange works`() {
        var a = alice
        var b = bob
        val (a1, msg1) = ratchet.encrypt(a, "ping".toByteArray())
        val (b1, _) = ratchet.decrypt(b, msg1)
        val (b2, msg2) = ratchet.encrypt(b1, "pong".toByteArray())
        val (_, plaintext) = ratchet.decrypt(a1, msg2)
        assertEquals("pong", String(plaintext))
        a = a1; b = b2
    }

    @Test
    fun `tampered ciphertext throws RatchetException`() {
        val (_, msg) = ratchet.encrypt(alice, "secret".toByteArray())
        val tampered = msg.copy(ciphertext = msg.ciphertext.also { it[it.size - 1] = it[it.size - 1].xor(0xFF.toByte()) })
        try {
            ratchet.decrypt(bob, tampered)
            fail("Expected RatchetException")
        } catch (e: RatchetException) {
            assertTrue(e.message!!.contains("Decryption failed"))
        }
    }

    @Test
    fun `old message keys are not reusable after decryption`() {
        val (aliceNext, msg) = ratchet.encrypt(alice, "once".toByteArray())
        val (bobNext, _) = ratchet.decrypt(bob, msg)
        try {
            ratchet.decrypt(bobNext, msg)
            fail("Expected RatchetException")
        } catch (e: RatchetException) {
            assertTrue(true)
        }
    }
}