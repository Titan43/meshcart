package com.meshcart.p2p.transport

import android.content.Context
import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId
import com.meshcart.p2p.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebRtcTransportAdapter(context: Context) : TransportPort {

    private val eglBase = EglBase.create()
    private val _incoming = MutableSharedFlow<MeshConnection>()
    override val incoming: Flow<MeshConnection> = _incoming

    private val factory: PeerConnectionFactory = run {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    override suspend fun connect(address: PeerAddress, localIdentity: Identity): MeshConnection {
        val (pc, channel) = buildPeerConnectionWithChannel(address.nodeId)
        val offer = pc.awaitOffer()
        pc.awaitSetLocalDescription(offer)
        address.candidates.forEach { c ->
            pc.addIceCandidate(org.webrtc.IceCandidate(c.sdpMid, c.sdpMLineIndex, c.sdp))
        }
        return WebRtcMeshConnection(address.nodeId, pc, channel)
    }

    override suspend fun listen(localIdentity: Identity) {}

    override fun stop() {
        factory.dispose()
        eglBase.release()
    }

    private fun buildPeerConnectionWithChannel(remoteNodeId: NodeId): Pair<PeerConnection, DataChannel> {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        var dataChannel: DataChannel? = null
        val pc = factory.createPeerConnection(config, MediaConstraints(), object : PeerConnection.Observer {
            override fun onIceCandidate(c: org.webrtc.IceCandidate?) {}
            override fun onIceCandidatesRemoved(c: Array<out org.webrtc.IceCandidate>?) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(r: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) { dataChannel = dc }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {}
        })!!
        val channel = pc.createDataChannel("meshcart", DataChannel.Init())
        return Pair(pc, channel)
    }

    private suspend fun PeerConnection.awaitOffer(): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) =
                    if (sdp != null) cont.resume(sdp)
                    else cont.resumeWithException(PeerConnectionException("null SDP"))
                override fun onCreateFailure(e: String?) =
                    cont.resumeWithException(PeerConnectionException("createOffer: $e"))
                override fun onSetSuccess() {}
                override fun onSetFailure(e: String?) {}
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.awaitSetLocalDescription(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(e: String?) =
                    cont.resumeWithException(PeerConnectionException("setLocalDescription: $e"))
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onCreateFailure(e: String?) {}
            }, sdp)
        }
}

private class WebRtcMeshConnection(
    override val remoteNodeId: NodeId,
    private val pc: PeerConnection,
    private val channel: DataChannel
) : MeshConnection {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    private val _incoming = MutableSharedFlow<ByteArray>()

    override val state: Flow<ConnectionState> = _state
    override val incoming: Flow<ByteArray> = _incoming

    init {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(a: Long) {}
            override fun onStateChange() {
                _state.value = when (channel.state()) {
                    DataChannel.State.OPEN -> ConnectionState.Connected
                    DataChannel.State.CLOSED -> ConnectionState.Disconnected
                    else -> ConnectionState.Connecting
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
                _incoming.tryEmit(bytes)
            }
        })
    }

    override suspend fun send(data: ByteArray) {
        if (!channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(data), true)))
            throw PeerConnectionException("DataChannel send failed")
    }

    override fun close() {
        channel.close()
        pc.close()
    }
}