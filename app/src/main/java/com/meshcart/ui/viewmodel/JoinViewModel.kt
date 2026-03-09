package com.meshcart.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshcart.identity.domain.Identity
import com.meshcart.p2p.transport.WebRtcTransportAdapter
import com.meshcart.sync.domain.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Base64
import java.util.zip.Inflater
import javax.inject.Inject

sealed interface JoinUiState {
    data object Idle : JoinUiState
    data class Connecting(val input: String) : JoinUiState
    data object Connected : JoinUiState
    data class Error(val input: String, val message: String) : JoinUiState
}

@HiltViewModel
class JoinViewModel @Inject constructor(
    private val identity: Identity,
    private val transport: WebRtcTransportAdapter,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _state = MutableStateFlow<JoinUiState>(JoinUiState.Idle)
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    fun submit(input: String) {
        if (input.isBlank()) return
        _state.value = JoinUiState.Connecting(input)
        viewModelScope.launch {
            runCatching {
                val offerSdp     = urlToSdp(input.trim()) ?: throw Exception("Invalid invite link")
                val answerHandle = transport.createAnswer(offerSdp, identity.nodeId, viewModelScope)
                val connection   = answerHandle.connection
                syncEngine.onPeerConnected(connection)
                withTimeout(20_000) { connection.awaitOpen() }
                _state.value = JoinUiState.Connected
            }.onFailure {
                _state.value = JoinUiState.Error(input, it.message ?: "Failed to connect")
            }
        }
    }

    fun reset() { _state.value = JoinUiState.Idle }

    private fun urlToSdp(url: String): String? = runCatching {
        val encoded = android.net.Uri.parse(url).getQueryParameter("s") ?: return null
        String(inflate(Base64.getUrlDecoder().decode(encoded)))
    }.getOrNull()

    private fun inflate(input: ByteArray): ByteArray {
        val i = Inflater().also { it.setInput(input) }
        val buf = ByteArray(65536)
        return buf.copyOf(i.inflate(buf)).also { i.end() }
    }
}