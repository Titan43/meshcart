package com.meshcart.di

import android.content.Context
import com.meshcart.ratchet.crypto.DoubleRatchetAdapter
import com.meshcart.ratchet.crypto.X3dhAdapter
import com.meshcart.ratchet.domain.RatchetPort
import com.meshcart.ratchet.domain.RatchetSessionStoragePort
import com.meshcart.ratchet.domain.X3dhPort
import com.meshcart.ratchet.storage.EncryptedRatchetSessionStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides @Singleton
    fun provideRatchetPort(): RatchetPort = DoubleRatchetAdapter()

    @Provides @Singleton
    fun provideX3dhPort(): X3dhPort = X3dhAdapter()

    @Provides @Singleton
    fun provideRatchetSessionStorage(@ApplicationContext context: Context): RatchetSessionStoragePort =
        EncryptedRatchetSessionStorage(context)
}