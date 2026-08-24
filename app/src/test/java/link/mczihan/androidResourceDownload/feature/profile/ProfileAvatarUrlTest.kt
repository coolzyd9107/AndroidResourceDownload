package link.mczihan.androidResourceDownload.feature.profile

import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileAvatarUrlTest {
    @Test
    fun githubUserUsesBackendAvatarUrl() {
        val user = user(
            loginType = LoginType.GITHUB,
            avatarUrl = "https://avatars.githubusercontent.com/u/123",
        )

        assertEquals("https://avatars.githubusercontent.com/u/123", user.profileAvatarUrl())
    }

    @Test
    fun qqUserUsesTrustedBackendAvatarUrl() {
        val user = user(
            loginType = LoginType.QQ,
            avatarUrl = "https://thirdqq.qlogo.cn/g?b=oidb&k=avatar&s=100",
        )

        assertEquals(
            "https://thirdqq.qlogo.cn/g?b=oidb&k=avatar&s=100",
            user.profileAvatarUrl(),
        )
    }

    @Test
    fun qqUserUpgradesTrustedHttpAvatarUrl() {
        val user = user(
            loginType = LoginType.QQ,
            avatarUrl = "http://thirdqq.qlogo.cn/avatar.png",
        )

        assertEquals("https://thirdqq.qlogo.cn/avatar.png", user.profileAvatarUrl())
    }

    @Test
    fun qqUserRejectsUntrustedBackendAvatarUrl() {
        val untrustedHostUser = user(
            loginType = LoginType.QQ,
            avatarUrl = "https://example.com/avatar.png",
        )

        assertNull(untrustedHostUser.profileAvatarUrl())
    }

    @Test
    fun unsafeBackendAvatarUrlFallsBackToDefault() {
        val insecureUser = user(
            loginType = LoginType.GITHUB,
            avatarUrl = "http://example.com/avatar.png",
        )
        val untrustedHostUser = user(
            loginType = LoginType.GITHUB,
            avatarUrl = "https://example.com/avatar.png",
        )

        assertNull(insecureUser.profileAvatarUrl())
        assertNull(untrustedHostUser.profileAvatarUrl())
    }

    private fun user(
        loginType: LoginType,
        avatarUrl: String? = null,
    ) = User(
        id = "user",
        name = null,
        email = null,
        role = Role.USER,
        loginType = loginType,
        avatarUrl = avatarUrl,
    )
}
