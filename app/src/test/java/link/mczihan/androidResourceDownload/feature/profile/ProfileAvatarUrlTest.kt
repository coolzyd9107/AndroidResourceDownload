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
    fun numericQqEmailUsesQqAvatar() {
        val user = user(
            loginType = LoginType.EMAIL,
            email = "123456789@qq.com",
        )

        assertEquals(
            "https://q1.qlogo.cn/g?b=qq&nk=123456789&s=640",
            user.profileAvatarUrl(),
        )
    }

    @Test
    fun qqAliasDoesNotExposeAnAvatarRequest() {
        val user = user(
            loginType = LoginType.EMAIL,
            email = "member@qq.com",
        )

        assertNull(user.profileAvatarUrl())
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

    @Test
    fun nonAsciiDigitsAreNotAcceptedAsQqNumber() {
        val user = user(
            loginType = LoginType.EMAIL,
            email = "１２３４５６@qq.com",
        )

        assertNull(user.profileAvatarUrl())
    }

    @Test
    fun numericQqEmailUsesBackendNickname() {
        val user = user(
            loginType = LoginType.EMAIL,
            email = "123456789@qq.com",
        ).copy(name = "QQ 昵称")

        assertEquals("QQ 昵称", user.accountDisplayName(null))
    }

    @Test
    fun numericQqEmailNeverUsesQqNumberAsNickname() {
        val user = user(
            loginType = LoginType.EMAIL,
            email = "123456789@qq.com",
        ).copy(name = "123456789")

        assertEquals("QQ 用户", user.accountDisplayName(null))
        assertEquals("QQ 用户", user.accountDisplayName("123456789"))
    }

    @Test
    fun qqAliasKeepsEmailPrefixAsDisplayName() {
        val user = user(
            loginType = LoginType.EMAIL,
            email = "member@qq.com",
        ).copy(name = "Backend Name")

        assertEquals("member", user.accountDisplayName(null))
    }

    private fun user(
        loginType: LoginType,
        email: String? = null,
        avatarUrl: String? = null,
    ) = User(
        id = "user",
        name = null,
        email = email,
        role = Role.USER,
        loginType = loginType,
        avatarUrl = avatarUrl,
    )
}
