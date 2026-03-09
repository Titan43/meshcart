package com.meshcart.di

import android.content.Context
import com.meshcart.identity.crypto.BouncyCastleKeyAgreementAdapter
import com.meshcart.identity.crypto.BouncyCastleSignerAdapter
import com.meshcart.identity.domain.DefaultIdentityRepository
import com.meshcart.identity.domain.Identity
import com.meshcart.identity.domain.IdentityRepository
import com.meshcart.identity.mnemonic.Bip39MnemonicAdapter
import com.meshcart.identity.mnemonic.MnemonicPort
import com.meshcart.persistence.domain.IdentityStoragePort
import com.meshcart.persistence.storage.EncryptedFileIdentityStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IdentityModule {

    @Provides @Singleton
    fun provideMnemonicPort(): MnemonicPort = Bip39MnemonicAdapter()

    @Provides @Singleton
    fun provideIdentityRepository(mnemonicPort: MnemonicPort): IdentityRepository =
        DefaultIdentityRepository(
            mnemonicPort = mnemonicPort,
            signer = BouncyCastleSignerAdapter(),
            keyAgreement = BouncyCastleKeyAgreementAdapter()
        )

    @Provides @Singleton
    fun provideIdentityStorage(@ApplicationContext context: Context): IdentityStoragePort =
        EncryptedFileIdentityStorage(context)

    @Provides @Singleton
    fun provideIdentity(
        repository: IdentityRepository,
        storage: IdentityStoragePort
    ): Identity {
        val existing = storage.load()
        return if (existing != null) {
            repository.restore(existing)
        } else {
            repository.create().also { storage.save(it.mnemonic) }
        }
    }
}