package com.meshcart.ratchet.crypto

import com.meshcart.ratchet.domain.RatchetException
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom

private const val NONCE_SIZE = 12
private const val TAG_BITS = 128

internal fun chaChaEncrypt(key: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
    val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
    val cipher = ChaCha20Poly1305()
    cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
    val out = ByteArray(cipher.getOutputSize(plaintext.size))
    val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
    cipher.doFinal(out, len)
    return nonce + out
}

internal fun chaChaDecrypt(key: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
    if (ciphertext.size < NONCE_SIZE) throw RatchetException("Ciphertext too short")
    val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
    val data = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)
    val cipher = ChaCha20Poly1305()
    cipher.init(false, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
    val out = ByteArray(cipher.getOutputSize(data.size))
    val len = cipher.processBytes(data, 0, data.size, out, 0)
    return try {
        cipher.doFinal(out, len)
        out
    } catch (e: Exception) {
        throw RatchetException("Decryption failed — wrong key or tampered message", e)
    }
}