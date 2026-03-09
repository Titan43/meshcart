package com.meshcart.di

import android.content.Context
import com.meshcart.list.domain.ShoppingListStoragePort
import com.meshcart.list.storage.EncryptedFileShoppingListStorage
import com.meshcart.persistence.domain.ListStoragePort
import com.meshcart.persistence.storage.EncryptedFileListStorage
import com.meshcart.persistence.storage.SyncStateAdapter
import com.meshcart.sync.domain.SyncStatePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {

    @Provides @Singleton
    fun provideListStoragePort(@ApplicationContext context: Context): ListStoragePort =
        EncryptedFileListStorage(context)

    @Provides @Singleton
    fun provideShoppingListStoragePort(@ApplicationContext context: Context): ShoppingListStoragePort =
        EncryptedFileShoppingListStorage(context)

    @Provides @Singleton
    fun provideSyncStatePort(storage: ListStoragePort): SyncStatePort =
        SyncStateAdapter(storage)
}