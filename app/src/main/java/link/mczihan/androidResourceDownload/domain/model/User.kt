package link.mczihan.androidResourceDownload.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String?,
    val email: String?,
    val role: Role,
    val loginType: LoginType,
    val avatarUrl: String? = null,
) {
    @Deprecated("Use loginType", ReplaceWith("loginType"))
    val loginMethod: LoginMethod
        get() = LoginMethod.valueOf(loginType.name)

    @Deprecated("Use loginType")
    constructor(
        id: String,
        name: String?,
        email: String?,
        role: Role,
        loginMethod: LoginMethod,
        avatarUrl: String? = null,
    ) : this(
        id = id,
        name = name,
        email = email,
        role = role,
        loginType = LoginType.valueOf(loginMethod.name),
        avatarUrl = avatarUrl,
    )
}

@Serializable
enum class Role {
    USER,
    ADMIN,
}

@Serializable
enum class LoginType {
    GITHUB,
    EMAIL,
}

@Deprecated("Use LoginType")
enum class LoginMethod {
    GITHUB,
    EMAIL,
}
