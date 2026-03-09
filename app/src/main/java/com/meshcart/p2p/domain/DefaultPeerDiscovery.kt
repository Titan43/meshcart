package com.meshcart.p2p.domain

import com.meshcart.identity.domain.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class DefaultPeerDiscovery(
    private val dht: DhtPort,
    private val transport: TransportPort
) : PeerDiscoveryPort {

    private val _discovered = MutableSharedFlow<PeerAddress>()
    override val discovered: Flow<PeerAddress> = _discovered

    override suspend fun start(identity: Identity) {
        dht.bootstrap()
        dht.announce(identity)
        transport.listen(identity)
    }

    override fun stop() {
        transport.stop()
    }

    suspend fun addManualPeer(address: PeerAddress) {
        _discovered.emit(address)
    }
}