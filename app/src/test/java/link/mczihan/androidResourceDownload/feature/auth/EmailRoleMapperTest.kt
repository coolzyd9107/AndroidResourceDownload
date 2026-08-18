package link.mczihan.androidResourceDownload.feature.auth

import link.mczihan.androidResourceDownload.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailRoleMapperTest {
    @Test
    fun qqDomainMapsToUser() {
        assertEquals(Role.USER, roleForAllowedEmail("Demo@QQ.COM "))
    }

    @Test
    fun mczihanDomainMapsToAdmin() {
        assertEquals(Role.ADMIN, roleForAllowedEmail("admin@mczihan.link"))
    }

    @Test
    fun unsupportedOrMalformedEmailIsRejected() {
        assertNull(roleForAllowedEmail("user@example.com"))
        assertNull(roleForAllowedEmail("@qq.com"))
        assertNull(roleForAllowedEmail("user@qq.com@example.com"))
    }
}
