package link.mczihan.androidResourceDownload.domain.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val loginMethod: LoginMethod,
    val avatarUrl: String? = null,
)

enum class Role {
    USER,
    ADMIN,
}

enum class LoginMethod {
    GITHUB,
    EMAIL,
}
