package com.meshcart.p2p.qr

import com.meshcart.identity.domain.NodeId
import com.meshcart.p2p.domain.MeshIceCandidate
import com.meshcart.p2p.domain.PeerAddress
import com.meshcart.p2p.domain.QrPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class QrPeerAddressAdapter : QrPort {

    override fun encode(address: PeerAddress): String =
        Json.encodeToString(address.toDto())

    override fun decode(payload: String): PeerAddress =
        Json.decodeFromString<PeerAddressDto>(payload).toDomain()
}

@Serializable
private data class PeerAddressDto(
    val nodeId: String,
    val ufrag: String,
    val pwd: String,
    val candidates: List<IceCandidateDto>
)

@Serializable
private data class IceCandidateDto(val sdp: String, val mid: String, val idx: Int)

private fun PeerAddress.toDto() = PeerAddressDto(
    nodeId = nodeId.value,
    ufrag = iceUfrag,
    pwd = icePwd,
    candidates = candidates.map { IceCandidateDto(it.sdp, it.sdpMid, it.sdpMLineIndex) }
)

private fun PeerAddressDto.toDomain() = PeerAddress(
    nodeId = NodeId(nodeId),
    iceUfrag = ufrag,
    icePwd = pwd,
    candidates = candidates.map { MeshIceCandidate(it.sdp, it.mid, it.idx) }
)