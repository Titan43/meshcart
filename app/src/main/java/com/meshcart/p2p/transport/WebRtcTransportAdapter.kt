package com.meshcart.p2p.transport

import android.content.Context
import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.NodeId
import com.meshcart.p2p.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ICE_GATHER_TIMEOUT_MS = 4_000L
private const val ICE_HOST_DEBOUNCE_MS  =   400L

class WebRtcTransportAdapter(context: Context) : TransportPort {

    private val _incoming = MutableSharedFlow<MeshConnection>()
    override val incoming: Flow<MeshConnection> = _incoming

    val factory: PeerConnectionFactory = run {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    suspend fun createOffer(remoteNodeId: NodeId, scope: CoroutineScope): OfferHandle {
        val gatherDone    = CompletableDeferred<Unit>()
        val hasCandidate  = CompletableDeferred<Unit>()
        val stateFlow     = MutableStateFlow(DataChannel.State.CONNECTING)
        val incomingFlow  = MutableSharedFlow<ByteArray>()

        val pc = buildPc(
            onCandidate     = { hasCandidate.complete(Unit) },
            onGatheringDone = { gatherDone.complete(Unit) },
            onDataChannel   = null
        )
        val channel = pc.createDataChannel("mesh", DataChannel.Init()).also {
            it.registerObserver(dcObserver(stateFlow, incomingFlow, it))
        }

        val offer = pc.awaitCreateSdp(isOffer = true)
        pc.awaitSetLocalDescription(offer)
        awaitIceGathering(gatherDone, hasCandidate)

        return OfferHandle(pc, channel, pc.localDescription.description, remoteNodeId, stateFlow, incomingFlow, this, scope)
    }

    suspend fun createAnswer(offerSdp: String, remoteNodeId: NodeId, scope: CoroutineScope): AnswerHandle {
        val gatherDone   = CompletableDeferred<Unit>()
        val hasCandidate = CompletableDeferred<Unit>()
        val stateFlow    = MutableStateFlow(DataChannel.State.CONNECTING)
        val incomingFlow = MutableSharedFlow<ByteArray>()
        val remoteChannel = CompletableDeferred<DataChannel>()

        val pc = buildPc(
            onCandidate     = { hasCandidate.complete(Unit) },
            onGatheringDone = { gatherDone.complete(Unit) },
            onDataChannel   = { dc ->
                dc.registerObserver(dcObserver(stateFlow, incomingFlow, dc))
                remoteChannel.complete(dc)
            }
        )

        pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offerSdp))

        val channel = withTimeoutOrNull(8_000) { remoteChannel.await() }
            ?: run {
                pc.close()
                throw PeerConnectionException("Invite link has expired — ask the owner to generate a new one")
            }

        val answer = pc.awaitCreateSdp(isOffer = false)
        pc.awaitSetLocalDescription(answer)
        awaitIceGathering(gatherDone, hasCandidate)

        val conn = WebRtcMeshConnection(remoteNodeId, pc, channel, stateFlow, incomingFlow, this, scope)
        return AnswerHandle(conn, pc.localDescription.description)
    }

    suspend fun completeOffer(handle: OfferHandle, answerSdp: String): MeshConnection {
        handle.pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
        val conn = WebRtcMeshConnection(
            handle.remoteNodeId, handle.pc, handle.channel,
            handle.stateFlow, handle.incomingFlow, this, handle.scope
        )
        conn.awaitOpen()
        return conn
    }

    override suspend fun connect(address: PeerAddress, localIdentity: Identity): MeshConnection =
        throw UnsupportedOperationException("Use createOffer/createAnswer")
    override suspend fun listen(localIdentity: Identity) {}
    override fun stop() { factory.dispose() }

    internal suspend fun PeerConnection.awaitSetLocalDescription(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(e: String?) = cont.resumeWithException(PeerConnectionException("setLocal: $e"))
                override fun onCreateSuccess(s: SessionDescription?) {}
                override fun onCreateFailure(e: String?) {}
            }, sdp)
        }

    internal suspend fun PeerConnection.awaitSetRemoteDescription(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(e: String?) = cont.resumeWithException(PeerConnectionException("setRemote: $e"))
                override fun onCreateSuccess(s: SessionDescription?) {}
                override fun onCreateFailure(e: String?) {}
            }, sdp)
        }

    internal suspend fun PeerConnection.awaitCreateSdp(isOffer: Boolean): SessionDescription =
        suspendCancellableCoroutine { cont ->
            val obs = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) =
                    if (sdp != null) cont.resume(sdp)
                    else cont.resumeWithException(PeerConnectionException("null SDP"))
                override fun onCreateFailure(e: String?) = cont.resumeWithException(PeerConnectionException("createSdp: $e"))
                override fun onSetSuccess() {}
                override fun onSetFailure(e: String?) {}
            }
            if (isOffer) createOffer(obs, MediaConstraints())
            else         createAnswer(obs, MediaConstraints())
        }

    internal suspend fun awaitIceGathering(
        gatheringComplete: CompletableDeferred<Unit>,
        hasCandidate:      CompletableDeferred<Unit>
    ) = coroutineScope {
        val deadline = launch { delay(ICE_GATHER_TIMEOUT_MS); gatheringComplete.complete(Unit) }
        val debounce = launch { hasCandidate.await(); delay(ICE_HOST_DEBOUNCE_MS); gatheringComplete.complete(Unit) }
        gatheringComplete.await()
        deadline.cancel(); debounce.cancel()
    }

    internal fun buildPc(
        onCandidate:      (IceCandidate) -> Unit,
        onGatheringDone:  () -> Unit,
        onDataChannel:    ((DataChannel) -> Unit)?,
        onIceStateChange: ((PeerConnection.IceConnectionState?) -> Unit)? = null
    ): PeerConnection = factory.createPeerConnection(
        PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        },
        object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate?)       { c?.let(onCandidate) }
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {
                if (s == PeerConnection.IceGatheringState.COMPLETE) onGatheringDone()
            }
            override fun onDataChannel(dc: DataChannel?)        { dc?.let { onDataChannel?.invoke(it) } }
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) { onIceStateChange?.invoke(s) }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState?)  {}
            override fun onIceConnectionReceivingChange(r: Boolean)  {}
            override fun onAddStream(s: MediaStream?)            {}
            override fun onRemoveStream(s: MediaStream?)         {}
            override fun onRenegotiationNeeded()                 {}
            override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {}
        }
    )!!

    private fun dcObserver(
        stateFlow:    MutableStateFlow<DataChannel.State>,
        incomingFlow: MutableSharedFlow<ByteArray>,
        dc:           DataChannel
    ) = object : DataChannel.Observer {
        override fun onBufferedAmountChange(a: Long) {}
        override fun onStateChange() { stateFlow.value = dc.state() }
        override fun onMessage(b: DataChannel.Buffer) {
            val bytes = ByteArray(b.data.remaining()).also { b.data.get(it) }
            incomingFlow.tryEmit(bytes)
        }
    }
}

// ── Handles ───────────────────────────────────────────────────────────────────

class OfferHandle(
    internal val pc:           PeerConnection,
    internal val channel:      DataChannel,
    val offerSdp:              String,
    internal val remoteNodeId: NodeId,
    internal val stateFlow:    MutableStateFlow<DataChannel.State>,
    internal val incomingFlow: MutableSharedFlow<ByteArray>,
    internal val adapter:      WebRtcTransportAdapter,
    internal val scope:        CoroutineScope
)

class AnswerHandle(
    val connection: WebRtcMeshConnection,
    val answerSdp:  String
)

// ── WebRtcMeshConnection ──────────────────────────────────────────────────────

class WebRtcMeshConnection(
    override val remoteNodeId: NodeId,
    private val pc:            PeerConnection,
    private val channel:       DataChannel,
    private val stateFlow:     MutableStateFlow<DataChannel.State>,
    private val incomingFlow:  MutableSharedFlow<ByteArray>,
    private val adapter:       WebRtcTransportAdapter,
    private val scope:         CoroutineScope
) : MeshConnection, RenegotiationPort {

    private val _connState     = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    private val _restartOffer  = MutableSharedFlow<IceRestartOffer>()
    private val _restartAnswer = MutableSharedFlow<IceRestartAnswer>()
    private var isRestarting   = false

    override val state:                 Flow<ConnectionState> = _connState
    override val incoming:              Flow<ByteArray>       = incomingFlow
    override val outboundRestartOffer:  Flow<IceRestartOffer>  = _restartOffer
    override val outboundRestartAnswer: Flow<IceRestartAnswer> = _restartAnswer

    init {
        stateFlow.onEach { dc ->
            _connState.value = when (dc) {
                DataChannel.State.OPEN   -> { isRestarting = false; ConnectionState.Connected }
                DataChannel.State.CLOSED -> if (isRestarting) ConnectionState.Reconnecting else ConnectionState.Disconnected
                else                     -> if (isRestarting) ConnectionState.Reconnecting else ConnectionState.Connecting
            }
        }.launchIn(scope)
    }

    fun onIceConnectionStateChanged(s: PeerConnection.IceConnectionState?) {
        when (s) {
            PeerConnection.IceConnectionState.DISCONNECTED,
            PeerConnection.IceConnectionState.FAILED -> if (!isRestarting) {
                isRestarting = true
                _connState.value = ConnectionState.Reconnecting
                scope.launch { triggerIceRestart() }
            }
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> isRestarting = false
            else -> {}
        }
    }

    private suspend fun triggerIceRestart() {
        runCatching {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            val offer = pc.awaitCreateSdp(isOffer = true, constraints)
            with(adapter) { pc.awaitSetLocalDescription(offer) }
            _restartOffer.emit(IceRestartOffer(offer.description))
        }.onFailure {
            isRestarting = false
            _connState.value = ConnectionState.Failed("ICE restart failed: ${it.message}")
        }
    }

    override suspend fun applyRestartOffer(offer: IceRestartOffer) {
        isRestarting = true
        _connState.value = ConnectionState.Reconnecting
        runCatching {
            with(adapter) { pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offer.offerSdp)) }
            val answer = pc.awaitCreateSdp(isOffer = false)
            with(adapter) { pc.awaitSetLocalDescription(answer) }
            _restartAnswer.emit(IceRestartAnswer(answer.description))
        }.onFailure {
            isRestarting = false
        }
    }

    override fun applyRestartAnswer(answer: IceRestartAnswer) {
        scope.launch {
            runCatching {
                with(adapter) { pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, answer.answerSdp)) }
            }
        }
    }

    suspend fun awaitOpen() {
        if (channel.state() == DataChannel.State.OPEN) return
        withTimeout(30_000) { _connState.first { it is ConnectionState.Connected } }
    }

    override suspend fun send(data: ByteArray) {
        if (_connState.value is ConnectionState.Reconnecting)
            withTimeout(30_000) { _connState.first { it is ConnectionState.Connected } }
        if (channel.state() != DataChannel.State.OPEN) awaitOpen()
        if (!channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(data), true)))
            throw PeerConnectionException("DataChannel send failed")
    }

    override fun close() { scope.cancel(); channel.close(); pc.close() }

    // Private helper so ICE restart can pass custom constraints
    private suspend fun PeerConnection.awaitCreateSdp(
        isOffer:     Boolean,
        constraints: MediaConstraints = MediaConstraints()
    ): SessionDescription = suspendCancellableCoroutine { cont ->
        val obs = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) =
                if (sdp != null) cont.resume(sdp)
                else cont.resumeWithException(PeerConnectionException("null SDP"))
            override fun onCreateFailure(e: String?) = cont.resumeWithException(PeerConnectionException(e ?: "sdp failed"))
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
        }
        if (isOffer) createOffer(obs, constraints) else createAnswer(obs, constraints)
    }
}