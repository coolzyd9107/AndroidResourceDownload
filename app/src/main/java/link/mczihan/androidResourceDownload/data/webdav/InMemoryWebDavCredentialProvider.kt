package link.mczihan.androidResourceDownload.data.webdav

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialLoader
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException

class InMemoryWebDavCredentialProvider(
    private val loader: WebDavCredentialLoader,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val expirySkewMillis: Long = 30_000L,
) : WebDavCredentialProvider {
    private val mutex = Mutex()
    private var cached: CredentialLease? = null
    private var generation: Long = 0L

    init {
        require(expirySkewMillis >= 0L) { "Expiry skew must not be negative" }
    }

    override suspend fun acquire(): CredentialLease = mutex.withLock {
        cached?.takeUnless {
            it.credential.isExpired(nowEpochMillis(), expirySkewMillis)
        }?.let { return@withLock it }

        val credential = try {
            loader.load()
        } catch (error: WebDavException) {
            throw error
        } catch (error: Exception) {
            throw WebDavException.CredentialUnavailable(error)
        }
        if (credential.isExpired(nowEpochMillis(), expirySkewMillis)) {
            throw WebDavException.CredentialUnavailable()
        }
        generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
        CredentialLease(credential, generation).also { cached = it }
    }

    override suspend fun invalidate(generation: Long) {
        mutex.withLock {
            if (cached?.generation == generation) cached = null
        }
    }

    override suspend fun clear() {
        mutex.withLock { cached = null }
    }
}
