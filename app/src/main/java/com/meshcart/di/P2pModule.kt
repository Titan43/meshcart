package com.meshcart.di

import android.content.Context
import com.meshcart.p2p.domain.TransportPort
import com.meshcart.p2p.signal.MqttSignalingClient
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
    fun provideMqttSignalingClient(): MqttSignalingClient = MqttSignalingClient()
}