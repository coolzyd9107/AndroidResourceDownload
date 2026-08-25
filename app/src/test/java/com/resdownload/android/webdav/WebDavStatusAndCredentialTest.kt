package com.resdownload.android.webdav

import com.resdownload.android.domain.webdav.WebDavCredential
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavPermission
import com.resdownload.android.domain.webdav.WebDavStatusMapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavStatusAndCredentialTest {
    @Test
    fun statusCodesMapToTypedErrors() {
        assertTrue(WebDavStatusMapper.exceptionFor(401) is WebDavException.AuthenticationRequired)
        assertTrue(WebDavStatusMapper.exceptionFor(403) is WebDavException.PermissionDenied)
        assertTrue(WebDavStatusMapper.exceptionFor(404) is WebDavException.NotFound)
        assertTrue(WebDavStatusMapper.exceptionFor(412) is WebDavException.PreconditionFailed)
        assertTrue(WebDavStatusMapper.exceptionFor(423) is WebDavException.Locked)
        assertTrue(WebDavStatusMapper.exceptionFor(503) is WebDavException.ServerError)
    }

    @Test
    fun expiryIncludesSkewAndPasswordIsRedacted() {
        val credential = WebDavCredential(
            username = "reader",
            password = "do-not-print",
            permission = WebDavPermission.READ_ONLY,
            expiresAtEpochMillis = 10_000L,
        )

        assertFalse(credential.isExpired(nowEpochMillis = 8_999L, skewMillis = 1_000L))
        assertTrue(credential.isExpired(nowEpochMillis = 9_000L, skewMillis = 1_000L))
        assertFalse(credential.toString().contains("do-not-print"))
    }
}
