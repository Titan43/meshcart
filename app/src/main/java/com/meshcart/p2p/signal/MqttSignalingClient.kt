package com.meshcart.p2p.signal

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "MqttSignaling"
private const val BASE = "https://ntfy.sh"

/**
 * Signaling client backed by ntfy.sh — free HTTPS pub/sub, port 443, no auth, no library.
 *
 * SDP payloads are base64-encoded before publishing to preserve newlines and avoid
 * ntfy.sh's notification-message character restrictions.
 *
 * Poll loop replaces MQTT subscribe — ntfy.sh ?poll=1&since=all returns the cached
 * message instantly without a long-lived connection.
 */
class MqttSignalingClient {

    suspend fun connect() {
        Log.d(TAG, "ntfy.sh signaling ready ✓")
    }

    fun disconnect() { /* no-op — no persistent connection */ }

    suspend fun publishRetained(topic: String, payload: String) = withContext(Dispatchers.IO) {
        val t = flatTopic(topic)
        // Base64-encode so multiline SDP survives ntfy.sh's message field intact
        val encoded = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        Log.d(TAG, "→ publishing to $t (${encoded.length} base64 chars)")
        val conn = (URL("$BASE/$t").openConnection() as HttpURLConnection).apply {
            requestMethod  = "POST"
            doOutput       = true
            connectTimeout = 10_000
            readTimeout    = 10_000
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            // ntfy.sh caches the last message per topic for 10 min — acts as "retained"
        }
        conn.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw SignalingException("Publish failed: HTTP $code")
        Log.d(TAG, "Published to $t ✓")
    }

    suspend fun clearRetained(topic: String) {
        Log.d(TAG, "clearRetained ${flatTopic(topic)} — ntfy.sh expires automatically")
    }

    suspend fun subscribe(topic: String) {
        Log.d(TAG, "subscribe ${flatTopic(topic)} (poll-on-demand)")
    }

    /**
     * Poll until a message appears on [topic], retrying every 2s up to [timeoutMs].
     * Returns the decoded (original) SDP string.
     */
    suspend fun awaitMessage(topic: String, timeoutMs: Long = 120_000): String {
        val t = flatTopic(topic)
        Log.d(TAG, "Awaiting message on $t …")
        return withTimeout(timeoutMs) {
            var result: String? = null
            while (result == null) {
                result = pollOnce(t)
                if (result == null) delay(2_000)
            }
            Log.d(TAG, "Got message on $t ✓")
            result
        }
    }

    // ── private ────────────────────────────────────────────────────────────────

    /** ntfy.sh topics must be flat — replace path separators with underscores. */
    private fun flatTopic(topic: String) = topic.replace("/", "_")

    private suspend fun pollOnce(topic: String): String? = withContext(Dispatchers.IO) {
        try {
            // poll=1 → don't block; since=all → return cached messages from start of cache
            val conn = (URL("$BASE/$topic/json?poll=1&since=all").openConnection()
                    as HttpURLConnection).apply {
                requestMethod  = "GET"
                connectTimeout = 8_000
                readTimeout    = 8_000
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return@withContext null }

            val lines = BufferedReader(InputStreamReader(conn.inputStream))
                .readLines()
                .filter { it.isNotBlank() }
            conn.disconnect()

            // ntfy.sh returns one JSON object per line; we want the last "message" event
            val encoded = lines
                .lastOrNull()
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.takeIf { it.optString("event") == "message" }
                ?.optString("message")
                ?.takeIf { it.isNotEmpty() }
                ?: return@withContext null

            // Decode base64 → original SDP
            String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Poll error: ${e.message}")
            null
        }
    }

    fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

class SignalingException(message: String) : Exception(message)