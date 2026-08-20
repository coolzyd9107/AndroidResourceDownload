package link.mczihan.androidResourceDownload.auth

import link.mczihan.androidResourceDownload.data.auth.BackendUserDto
import link.mczihan.androidResourceDownload.data.auth.toDomain
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserMappingTest {
    @Test
    fun nullableGitHubIdentityFieldsArePreserved() {
        val user = BackendUserDto(
            id = "github-user",
            name = null,
            email = null,
            role = "USER",
            avatarUrl = null,
            loginType = "GITHUB",
        ).toDomain()

        assertNull(user.name)
        assertNull(user.email)
        assertEquals(LoginType.GITHUB, user.loginType)
        assertEquals(Role.USER, user.role)
    }

    @Test
    fun githubLoginTypeDoesNotDetermineRole() {
        val avatarUrl = "https://avatars.githubusercontent.com/u/123"
        val user = BackendUserDto(
            id = "github-admin",
            name = "Admin",
            email = null,
            role = "ADMIN",
            avatarUrl = avatarUrl,
            loginType = "GITHUB",
        ).toDomain()

        assertEquals(LoginType.GITHUB, user.loginType)
        assertEquals(Role.ADMIN, user.role)
        assertEquals(avatarUrl, user.avatarUrl)
    }

    @Test
    fun emailLoginTypeDoesNotDetermineRole() {
        val user = BackendUserDto(
            id = "email-user",
            name = null,
            email = "member@qq.com",
            role = "USER",
            loginType = "EMAIL",
        ).toDomain()

        assertEquals(LoginType.EMAIL, user.loginType)
        assertEquals(Role.USER, user.role)
    }
}
