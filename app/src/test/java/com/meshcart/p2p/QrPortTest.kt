package com.meshcart.p2p

import com.meshcart.identity.domain.NodeId
import com.meshcart.p2p.domain.MeshIceCandidate
import com.meshcart.p2p.domain.PeerAddress
import com.meshcart.p2p.qr.QrPeerAddressAdapter
import org.junit.Assert.*
import org.junit.Test

class QrPortTest {

    private val qr = QrPeerAddressAdapter()

    @Test
    fun `encode and decode roundtrip produces equal PeerAddress`() {
        val address = PeerAddress(
            nodeId = NodeId("a".repeat(64)),
            iceUfrag = "uf123",
            icePwd = "pwd456",
            candidates = listOf(
                MeshIceCandidate("candidate:1 1 UDP 123 192.168.1.1 5000 typ host", "0", 0)
            )
        )
        assertEquals(address, qr.decode(qr.encode(address)))
    }

    @Test
    fun `encoded payload is valid JSON string`() {
        val address = PeerAddress(
            nodeId = NodeId("b".repeat(64)),
            iceUfrag = "x",
            icePwd = "y",
            candidates = emptyList()
        )
        val payload = qr.encode(address)
        assertTrue(payload.startsWith("{"))
        assertTrue(payload.contains("nodeId"))
    }

    @Test
    fun `different addresses produce different payloads`() {
        val a = PeerAddress(NodeId("a".repeat(64)), "u1", "p1", emptyList())
        val b = PeerAddress(NodeId("b".repeat(64)), "u2", "p2", emptyList())
        assertNotEquals(qr.encode(a), qr.encode(b))
    }
}