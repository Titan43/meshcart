package com.meshcart.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeepLinkModule {

    @Provides
    @Singleton
    fun provideDeepLinkFlow(): MutableSharedFlow<String> =
        MutableSharedFlow(replay = 1, extraBufferCapacity = 0)
}