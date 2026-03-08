package com.meshcart.identity.domain

@JvmInline value class NodeId(val value: String) {
    init { require(value.matches(Regex("[0-9a-f]{64}"))) }
    override fun toString() = value
}

@JvmInline value class SharedSecret(val bytes: ByteArray) {
    override fun toString() = "SharedSecret[redacted]"
}

class InvalidMnemonicException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)