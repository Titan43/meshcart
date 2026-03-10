package com.meshcart.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.meshcart.identity.domain.Identity
import com.meshcart.p2p.signal.MqttSignalingClient
import com.meshcart.p2p.transport.WebRtcTransportAdapter
import com.meshcart.sync.domain.ListId
import com.meshcart.sync.domain.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed interface ShareUiState {
    data object Generating : ShareUiState
    data class Ready(val code: String, val qr: Bitmap) : ShareUiState
    data object Connecting : ShareUiState
    data object Connected : ShareUiState
    data class Error(val message: String) : ShareUiState
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identity: Identity,
    private val transport: WebRtcTransportAdapter,
    private val signaling: MqttSignalingClient,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val listId = ListId(checkNotNull(savedStateHandle["listId"]))
    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Generating)
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    // Dedicated scope that survives dispatcher switches — not tied to Main job hierarchy
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init { start() }

    fun retry() { start() }

    override fun onCleared() {
        super.onCleared()
        ioScope.cancel()
    }

    private fun setState(s: ShareUiState) {
        viewModelScope.launch(Dispatchers.Main.immediate) { _state.value = s }
    }

    private fun start() {
        setState(ShareUiState.Generating)
        ioScope.launch {
            runCatching {
                // 1. Connect MQTT
                signaling.connect()

                // 2. Generate code + create offer
                val code        = signaling.generateCode()
                val offerTopic  = "meshcart/signal/$code/offer"
                val answerTopic = "meshcart/signal/$code/answer"

                // 3. Subscribe to answer topic BEFORE publishing offer
                signaling.subscribe(answerTopic)

                // 4. Create WebRTC offer (needs its own scope, not tied to IO dispatcher)
                val handle = withContext(Dispatchers.Default) {
                    transport.createOffer(identity.nodeId, ioScope)
                }

                // 5. Publish offer as RETAINED so joiner gets it even if they connect later
                signaling.publishRetained(offerTopic, handle.offerSdp)

                // 6. Show code + QR to user
                val qr = makeQr(code)
                setState(ShareUiState.Ready(code = code, qr = qr))

                // 7. Wait for joiner's answer
                val answerSdp = signaling.awaitMessage(answerTopic)
                setState(ShareUiState.Connecting)

                // 8. Clean up retained messages — no one else should use these topics
                signaling.clearRetained(offerTopic)
                signaling.clearRetained(answerTopic)

                // 9. Complete WebRTC handshake
                val connection = transport.completeOffer(handle, answerSdp)
                syncEngine.onPeerConnected(connection)
                setState(ShareUiState.Connected)

            }.onFailure { e ->
                Log.e(TAG, "Share failed: ${e.message}", e)
                setState(ShareUiState.Error(e.message ?: "Failed to connect"))
            }
        }
    }

    private fun makeQr(code: String): Bitmap {
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 2)
        }
        // Encode deep link so scanning opens the app and pre-fills the join field
        val content = "meshcart://join?code=$code"
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        return Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565).also { bmp ->
            for (x in 0 until 512) for (y in 0 until 512)
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
    }

    companion object { private const val TAG = "ShareViewModel" }
}