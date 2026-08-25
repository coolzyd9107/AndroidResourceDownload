package com.resdownload.android.auth

import com.resdownload.android.data.auth.BackendUserDto
import com.resdownload.android.data.auth.toDomain
import com.resdownload.android.domain.model.LoginType
import com.resdownload.android.domain.model.Role
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
    fun qqLoginTypeDoesNotDetermineRole() {
        val user = BackendUserDto(
            id = "qq-user",
            name = "QQ User",
            email = null,
            role = "USER",
            loginType = "QQ",
        ).toDomain()

        assertEquals(LoginType.QQ, user.loginType)
        assertEquals(Role.USER, user.role)
    }
}
