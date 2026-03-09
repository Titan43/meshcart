package com.meshcart.di

import com.meshcart.identity.domain.Identity
import com.meshcart.ratchet.domain.RatchetPort
import com.meshcart.ratchet.domain.RatchetSessionStoragePort
import com.meshcart.ratchet.domain.X3dhPort
import com.meshcart.sync.access.SignatureListAccessAdapter
import com.meshcart.sync.crdt.OrSetCrdtAdapter
import com.meshcart.sync.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides @Singleton
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun provideCrdtPort(): CrdtPort = OrSetCrdtAdapter()

    @Provides @Singleton
    fun provideListAccessPort(identity: Identity): ListAccessPort =
        SignatureListAccessAdapter(identity)

    @Provides @Singleton
    fun provideSyncPort(
        identity: Identity,
        x3dh: X3dhPort,
        ratchet: RatchetPort,
        sessions: RatchetSessionStoragePort
    ): SyncPort = SyncAdapter(identity, x3dh, ratchet, sessions)

    @Provides @Singleton
    fun provideSyncEngine(
        identity: Identity,
        sync: SyncPort,
        crdt: CrdtPort,
        access: ListAccessPort,
        state: SyncStatePort,
        scope: CoroutineScope
    ): SyncEngine = SyncEngine(identity, sync, crdt, access, state, scope)
}