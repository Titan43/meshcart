package com.meshcart.p2p.domain

import com.meshcart.identity.domain.NodeId

data class MeshIceCandidate(val sdp: String, val sdpMid: String, val sdpMLineIndex: Int)

data class PeerAddress(
    val nodeId: NodeId,
    val iceUfrag: String,
    val icePwd: String,
    val candidates: List<MeshIceCandidate>
)

sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

class PeerConnectionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)