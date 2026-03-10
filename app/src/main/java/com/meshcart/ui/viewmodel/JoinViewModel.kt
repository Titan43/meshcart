package com.meshcart.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshcart.identity.domain.Identity
import com.meshcart.p2p.signal.MqttSignalingClient
import com.meshcart.p2p.transport.WebRtcTransportAdapter
import com.meshcart.sync.domain.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed interface JoinUiState {
    data object Idle : JoinUiState
    data class Connecting(val code: String) : JoinUiState
    data object Connected : JoinUiState
    data class Error(val code: String, val message: String) : JoinUiState
}

@HiltViewModel
class JoinViewModel @Inject constructor(
    private val identity: Identity,
    private val transport: WebRtcTransportAdapter,
    private val signaling: MqttSignalingClient,
    private val syncEngine: SyncEngine,
    deepLinkFlow: MutableSharedFlow<String>
) : ViewModel() {

    private val _state = MutableStateFlow<JoinUiState>(JoinUiState.Idle)
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Exposed so ListsScreen can open the join panel and pre-fill the input
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    init {
        viewModelScope.launch {
            deepLinkFlow.collect { url ->
                deepLinkFlow.resetReplayCache()
                val code = android.net.Uri.parse(url).getQueryParameter("code")
                    ?.uppercase()?.trim()
                    ?: return@collect
                // If already connecting with same code, ignore
                if (_state.value is JoinUiState.Connecting) return@collect
                // Signal ListsScreen to open join panel and fill this code
                _pendingCode.value = code
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ioScope.cancel()
    }

    private fun setState(s: JoinUiState) {
        viewModelScope.launch(Dispatchers.Main.immediate) { _state.value = s }
    }

    fun submit(rawCode: String) {
        val code = rawCode.uppercase().trim()
        if (code.length != 6 || _state.value is JoinUiState.Connecting) return
        setState(JoinUiState.Connecting(code))

        ioScope.launch {
            runCatching {
                val offerTopic  = "meshcart/signal/$code/offer"
                val answerTopic = "meshcart/signal/$code/answer"

                // 1. Connect + subscribe to offer topic
                signaling.connect()
                signaling.subscribe(offerTopic)

                // 2. Fetch offer — retained means we get it even if owner published earlier
                val offerSdp = signaling.awaitMessage(offerTopic)

                // 3. Create WebRTC answer
                val answerHandle = withContext(Dispatchers.Default) {
                    transport.createAnswer(offerSdp, identity.nodeId, ioScope)
                }

                // 4. Publish answer as retained so owner gets it even if slightly delayed
                signaling.publishRetained(answerTopic, answerHandle.answerSdp)

                // 5. Register + wait for P2P connection
                syncEngine.onPeerConnected(answerHandle.connection)
                answerHandle.connection.awaitOpen()
                setState(JoinUiState.Connected)

            }.onFailure { e ->
                Log.e(TAG, "Join failed: ${e.message}", e)
                setState(JoinUiState.Error(code, e.message ?: "Failed to connect"))
            }
        }
    }

    fun consumePendingCode() { _pendingCode.value = null }

    fun reset() { setState(JoinUiState.Idle) }

    companion object { private const val TAG = "JoinViewModel" }
}