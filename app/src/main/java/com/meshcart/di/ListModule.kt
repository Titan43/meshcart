package com.meshcart.di

import com.meshcart.list.domain.*
import com.meshcart.persistence.domain.ListStoragePort
import com.meshcart.sync.domain.CrdtPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ListModule {

    @Provides @Singleton
    fun provideShoppingListRepository(
        storage: ShoppingListStoragePort
    ): ShoppingListRepository = DefaultShoppingListRepository(storage)

    @Provides @Singleton
    fun provideShoppingItemRepository(
        crdt: CrdtPort,
        storage: ListStoragePort,
        listRepository: ShoppingListRepository
    ): ShoppingItemRepository = DefaultShoppingItemRepository(crdt, storage, listRepository)
}