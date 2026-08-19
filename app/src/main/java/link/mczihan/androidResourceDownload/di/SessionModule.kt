package link.mczihan.androidResourceDownload.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.core.security.EncryptedSessionStore
import link.mczihan.androidResourceDownload.core.security.SessionStore

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindSessionStore(implementation: EncryptedSessionStore): SessionStore
}
