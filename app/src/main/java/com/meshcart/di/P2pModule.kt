package com.meshcart.di

import android.content.Context
import com.meshcart.p2p.domain.DefaultPeerDiscovery
import com.meshcart.p2p.domain.DhtPort
import com.meshcart.p2p.domain.PeerDiscoveryPort
import com.meshcart.p2p.domain.QrPort
import com.meshcart.p2p.domain.TransportPort
import com.meshcart.p2p.dht.KademliaDhtAdapter
import com.meshcart.p2p.qr.QrPeerAddressAdapter
import com.meshcart.p2p.transport.WebRtcTransportAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object P2pModule {

    @Provides @Singleton
    fun provideWebRtcAdapter(@ApplicationContext context: Context): WebRtcTransportAdapter =
        WebRtcTransportAdapter(context)

    @Provides @Singleton
    fun provideTransportPort(adapter: WebRtcTransportAdapter): TransportPort = adapter

    @Provides @Singleton
    fun provideDhtPort(transport: TransportPort): DhtPort =
        KademliaDhtAdapter(transport)

    @Provides @Singleton
    fun provideQrPort(): QrPort = QrPeerAddressAdapter()

    @Provides @Singleton
    fun providePeerDiscovery(dht: DhtPort, transport: TransportPort): PeerDiscoveryPort =
        DefaultPeerDiscovery(dht, transport)
}