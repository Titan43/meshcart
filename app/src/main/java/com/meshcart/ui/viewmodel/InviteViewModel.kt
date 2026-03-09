package com.meshcart.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.meshcart.identity.domain.Identity
import com.meshcart.list.domain.ShoppingListRepository
import com.meshcart.p2p.transport.OfferHandle
import com.meshcart.p2p.transport.WebRtcTransportAdapter
import com.meshcart.sync.domain.ListId
import com.meshcart.sync.domain.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.inject.Inject

sealed interface InviteUiState {
    data object Idle : InviteUiState
    data object GeneratingOffer : InviteUiState

    // Owner: SDP encoded as a short link + QR, waiting for joiner's answer
    data class WaitingForAnswer(
        val shareUrl: String,
        val qr: Bitmap?,
        val answerInput: String = "",
        val error: String? = null,
        val isConnecting: Boolean = false
    ) : InviteUiState

    // Joiner step 1: paste the owner's link
    data class EnterOffer(
        val input: String = "",
        val error: String? = null,
        val isProcessing: Boolean = false
    ) : InviteUiState

    // Joiner step 2: show answer link for owner to paste
    data class WaitingForOwner(
        val shareUrl: String,
        val qr: Bitmap?
    ) : InviteUiState

    data object Connected : InviteUiState
    data class Error(val message: String) : InviteUiState
}

@HiltViewModel
class InviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identity: Identity,
    private val transport: WebRtcTransportAdapter,
    private val syncEngine: SyncEngine,
    private val listRepository: ShoppingListRepository
) : ViewModel() {

    private val listId = ListId(checkNotNull(savedStateHandle["listId"]))
    private val _state = MutableStateFlow<InviteUiState>(InviteUiState.Idle)
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    private var offerHandle: OfferHandle? = null

    // ── Owner ─────────────────────────────────────────────────────────────────

    fun startAsOwner() {
        _state.value = InviteUiState.GeneratingOffer
        viewModelScope.launch {
            runCatching {
                val handle = transport.createOffer(identity.nodeId, viewModelScope)
                offerHandle = handle
                val url = sdpToUrl(handle.offerSdp, isOffer = true)
                val qr  = withContext(Dispatchers.Default) { makeQr(url) }
                _state.value = InviteUiState.WaitingForAnswer(shareUrl = url, qr = qr)
            }.onFailure {
                _state.value = InviteUiState.Error(it.message ?: "Failed to create offer")
            }
        }
    }

    fun onAnswerInput(value: String) {
        val s = _state.value as? InviteUiState.WaitingForAnswer ?: return
        _state.value = s.copy(answerInput = value, error = null)
    }

    fun submitAnswer() {
        val s = _state.value as? InviteUiState.WaitingForAnswer ?: return
        val handle = offerHandle ?: return
        _state.value = s.copy(isConnecting = true)
        viewModelScope.launch {
            runCatching {
                val answerSdp = urlToSdp(s.answerInput.trim())
                    ?: throw Exception("Invalid answer link")
                val connection = transport.completeOffer(handle, answerSdp)
                listRepository.addMember(listId, connection.remoteNodeId, identity.nodeId)
                syncEngine.onPeerConnected(connection)
                syncEngine.sendInvite(connection, listId, connection.remoteNodeId)
                _state.value = InviteUiState.Connected
            }.onFailure {
                val cur = _state.value as? InviteUiState.WaitingForAnswer ?: return@onFailure
                _state.value = cur.copy(isConnecting = false, error = it.message ?: "Connection failed")
            }
        }
    }

    // ── Joiner ────────────────────────────────────────────────────────────────

    fun startAsJoiner() { _state.value = InviteUiState.EnterOffer() }

    fun onOfferInput(value: String) {
        val s = _state.value as? InviteUiState.EnterOffer ?: return
        _state.value = s.copy(input = value, error = null)
    }

    fun submitOffer() {
        val s = _state.value as? InviteUiState.EnterOffer ?: return
        _state.value = s.copy(isProcessing = true)
        viewModelScope.launch {
            runCatching {
                val offerSdp = urlToSdp(s.input.trim())
                    ?: throw Exception("Invalid invite link")
                val answerHandle = transport.createAnswer(offerSdp, identity.nodeId, viewModelScope)
                val url = sdpToUrl(answerHandle.answerSdp, isOffer = false)
                val qr  = withContext(Dispatchers.Default) { makeQr(url) }
                _state.value = InviteUiState.WaitingForOwner(shareUrl = url, qr = qr)
                // Connection is already live — register it
                val connection = answerHandle.connection
                listRepository.addMember(listId, connection.remoteNodeId, identity.nodeId)
                syncEngine.onPeerConnected(connection)
                connection.awaitOpen()
                _state.value = InviteUiState.Connected
            }.onFailure {
                _state.value = InviteUiState.EnterOffer(
                    input = s.input,
                    error = it.message ?: "Failed to process invite"
                )
            }
        }
    }

    fun retry() { _state.value = InviteUiState.Idle }

    // ── SDP ↔ URL ─────────────────────────────────────────────────────────────
    // Compress SDP (it's repetitive text, deflates ~70%) then base64url-encode it.
    // Result fits in a scannable QR and a shareable link.

    private fun sdpToUrl(sdp: String, isOffer: Boolean): String {
        val compressed = deflate(sdp.toByteArray())
        val encoded    = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        val type       = if (isOffer) "o" else "a"
        val nodeHex    = identity.nodeId.value
        return "meshcart://sdp?t=$type&n=$nodeHex&s=$encoded"
    }

    private fun urlToSdp(url: String): String? = runCatching {
        val uri = android.net.Uri.parse(url)
        val encoded = uri.getQueryParameter("s") ?: return null
        val compressed = Base64.getUrlDecoder().decode(encoded)
        String(inflate(compressed))
    }.getOrNull()

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(input); finish() }
        val buf = ByteArray(input.size + 64)
        val len = deflater.deflate(buf)
        deflater.end()
        return buf.copyOf(len)
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val buf = ByteArray(65536)
        val len = inflater.inflate(buf)
        inflater.end()
        return buf.copyOf(len)
    }

    private fun makeQr(content: String): Bitmap {
        val hints = HashMap<EncodeHintType, Any>().apply { put(EncodeHintType.MARGIN, 1) }
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        return Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565).also { bmp ->
            for (x in 0 until 512) for (y in 0 until 512)
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
    }
}