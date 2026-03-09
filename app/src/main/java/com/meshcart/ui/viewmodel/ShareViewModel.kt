package com.meshcart.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
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
import javax.inject.Inject

sealed interface ShareUiState {
    data object Generating : ShareUiState
    data class Ready(val qr: Bitmap, val shareUrl: String) : ShareUiState
    data class Error(val message: String) : ShareUiState
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identity: Identity,
    private val transport: WebRtcTransportAdapter,
    private val syncEngine: SyncEngine,
    private val listRepository: ShoppingListRepository
) : ViewModel() {

    private val listId = ListId(checkNotNull(savedStateHandle["listId"]))
    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Generating)
    val state: StateFlow<ShareUiState> = _state.asStateFlow()
    private var offerHandle: OfferHandle? = null

    init { generateOffer() }

    fun retry() = generateOffer()

    private fun generateOffer() {
        _state.value = ShareUiState.Generating
        viewModelScope.launch {
            runCatching {
                val handle  = transport.createOffer(identity.nodeId, viewModelScope)
                offerHandle = handle
                val url     = sdpToUrl(handle.offerSdp)
                val qr      = withContext(Dispatchers.Default) { makeQr(url) }
                _state.value = ShareUiState.Ready(qr = qr, shareUrl = url)
            }.onFailure {
                _state.value = ShareUiState.Error(it.message ?: "Failed to create invite")
            }
        }
    }

    private fun sdpToUrl(sdp: String): String {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(deflate(sdp.toByteArray()))
        return "meshcart://sdp?n=${identity.nodeId.value}&s=$encoded"
    }

    private fun deflate(input: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(input); finish() }
        val buf = ByteArray(input.size + 64)
        return buf.copyOf(d.deflate(buf)).also { d.end() }
    }

    private fun makeQr(content: String): Bitmap {
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 1024, 1024, hints)
        return Bitmap.createBitmap(1024, 1024, Bitmap.Config.RGB_565).also { bmp ->
            for (x in 0 until 1024) for (y in 0 until 1024)
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
    }
}