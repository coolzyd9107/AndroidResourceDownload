package com.resdownload.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.resdownload.android.core.security.EncryptedSessionStore
import com.resdownload.android.core.security.SessionStore

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindSessionStore(implementation: EncryptedSessionStore): SessionStore
}
