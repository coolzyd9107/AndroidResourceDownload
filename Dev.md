# Android WebDAV 文件客户端开发文档  
## 版本：MVP v1.0  
## 技术栈：Kotlin / Jetpack Compose / Material 3 Expressive  
## 最低支持：Android 8.1（API 27）

---

# 1. 项目概述

本项目为 Android 客户端应用，使用 Kotlin 开发，采用 Material 3 Expressive（M3E）UI 风格，最低支持 Android 8.1。

应用核心目标：

- 用户登录与角色识别
- 基于 WebDAV 的文件列表浏览
- 文件下载，支持断点续传
- 管理员可进行 WebDAV 文件读写操作
- 支持远程加密更新 URL 下发
- 支持 App 版本更新
- 支持深色模式

---

# 2. 本期开发范围

## 2.1 本期必做功能

### 账号与权限

- 登录页
- GitHub OAuth 登录
- Email 验证码登录
- 用户角色识别
- 个人中心
- 退出登录

### 文件能力

- 文件列表浏览
- 文件详情
- 支持类型的免下载文本与图片预览
- 文件下载
- 下载进度展示
- 下载完成通知
- 下载失败重试
- 断点续传

### 管理员能力

- 文件上传
- 文件/目录移动与重命名
- 文件/目录复制
- 删除文件/目录
- 对具备版本校验条件的纯文本文件进行在线编辑

### 更新能力

- 加密更新 URL 获取
- 更新 URL 校验与解析
- App 版本更新
- 强制更新提示

### 系统能力

- 设置页
- 深色模式切换
- 统一错误处理
- Android 8.1 兼容适配

---

## 2.2 本期不做功能

以下功能本期不纳入开发范围：

- 文件搜索
- 文件收藏
- 最近访问
- 批量操作
- 回收站
- 操作日志后台
- 多账号切换
- 离线缓存
- 文件分享
- 管理后台 Web 端
- 灰度发布
- 审计后台

---

# 3. 用户角色与权限

## 3.1 角色定义

| 角色 | 邮箱规则 | 说明 |
|---|---|---|
| 普通用户 | `@qq.com` | 只读访问文件 |
| 管理员 | `@mczihan.link` | 可读、可写、可管理文件 |
| 未登录用户 | 无 | 仅可访问登录页 |

---

## 3.2 权限矩阵

| 功能 | 未登录 | 普通用户 | 管理员 |
|---|---:|---:|---:|
| 登录 | 是 | - | - |
| 查看文件列表 | 否 | 是 | 是 |
| 下载文件 | 否 | 是 | 是 |
| 上传文件 | 否 | 否 | 是 |
| 新建目录 | 否 | 否 | 是 |
| 删除文件/目录 | 否 | 否 | 是 |
| 重命名 | 否 | 否 | 是 |
| 获取加密更新 URL | 否 | 是 | 是 |
| App 更新 | 否 | 是 | 是 |
| 设置页 | 否 | 是 | 是 |
| 个人中心 | 否 | 是 | 是 |
| 退出登录 | 否 | 是 | 是 |

---

# 4. 产品流程设计

---

## 4.1 登录流程

### GitHub OAuth 登录流程

1. 用户点击「GitHub 登录」
2. App 打开 Custom Tabs / 系统浏览器
3. 用户授权 GitHub
4. GitHub 回调后端 redirect uri
5. 后端获取 GitHub 用户邮箱
6. 后端判断邮箱后缀：
   - `@qq.com`：普通用户
   - `@mczihan.link`：管理员
   - 其他：拒绝登录
7. 后端签发 App 自己的 token
8. App 登录成功，进入主页

---

### Email 验证码登录流程

1. 用户输入邮箱
2. 点击「获取验证码」
3. 后端发送验证码
4. 用户输入验证码
5. 后端校验邮箱与验证码
6. 根据邮箱后缀判断角色
7. 签发 token
8. App 登录成功

---

### 登录规则

- 仅允许以下邮箱后缀登录：
  - `@qq.com`
  - `@mczihan.link`
- 非允许后缀直接拒绝
- GitHub 登录必须能获取到符合规则的邮箱
- 首次登录自动创建用户
- 登录成功后本地保存 token 与用户信息

---

## 4.2 文件列表流程

1. 用户进入主页
2. App 请求 WebDAV 根目录或默认目录
3. 展示文件与文件夹列表
4. 点击文件夹进入子目录
5. 点击文件进入文件详情/下载面板
6. 支持返回上一级目录
7. 支持下拉刷新
8. 加载失败时展示错误页与重试按钮

---

## 4.3 文件下载流程

1. 用户点击文件
2. 展示文件详情
3. 用户点击「下载」
4. App 创建下载任务
5. 通知栏展示下载进度
6. 下载完成后通知用户
7. 用户可打开文件或查看下载结果

---

## 4.4 断点续传流程

1. App 检查本地是否存在未完成任务
2. 若存在临时文件，读取已下载大小
3. 请求服务端文件信息：
   - 是否支持 Range
   - ETag / Last-Modified
4. 若支持断点续传：
   - 请求头携带 `Range: bytes=已下载字节数-`
5. 继续写入临时文件
6. 下载完成后重命名为正式文件
7. 若服务端不支持断点续传：
   - 降级为完整下载
   - 失败后重新下载

---

## 4.5 管理员上传流程

1. 管理员进入目标目录
2. 选择上传一个或多个文件，或选择本地文件夹
3. App 为 SAF URI 保留只读授权，并将文件任务写入 Room 队列
4. 若选择文件夹，先按安全相对路径展开目录树并创建文件夹根目录、子目录和空目录
5. 上传前台服务最多并行传输三个文件；目录创建不计入文件并发数，超出限制的文件等待槽位
6. 上传页显示各任务进度、实时速度、等待、提交、失败和完成状态
7. 文件先上传到同目录唯一临时名，再以禁止覆盖的 `MOVE` 提交
8. 上传失败或取消后可从头重试；完成后可刷新云端目录查看原始结构

---

## 4.6 管理员新建目录流程

1. 管理员点击「新建目录」
2. 输入目录名
3. 校验名称合法性
4. 调用 WebDAV `MKCOL`
5. 创建成功后刷新列表

---

## 4.7 管理员删除流程

1. 管理员长按文件/目录
2. 点击删除
3. 弹出二次确认弹窗
4. 调用 WebDAV `DELETE`
5. 删除成功后刷新列表

---

## 4.8 管理员重命名流程

1. 管理员长按文件/目录
2. 点击重命名
3. 输入新名称
4. 调用 WebDAV `MOVE`
5. 成功后刷新列表

---

## 4.9 加密更新 URL 流程

1. App 请求更新信息
2. 服务端返回：
   - 版本号
   - 是否强制更新
   - 加密 URL
   - 过期时间
   - 签名
3. App 校验更新信息
4. App 请求解析加密 URL
5. 服务端校验权限与有效期
6. 返回可下载的临时 URL
7. App 开始下载 APK
8. 下载完成后提示安装

---

## 4.10 深色模式流程

用户可在设置页切换主题：

- 跟随系统
- 始终浅色
- 始终深色

App 根据用户选择实时切换主题。

---

# 5. 页面清单

本期需要开发以下页面：

## 5.1 登录页

页面元素：

- Logo
- GitHub 登录按钮
- Email 登录入口
- 用户协议勾选
- 隐私政策入口
- Loading
- 错误提示

---

## 5.2 Email 登录页

页面元素：

- 邮箱输入框
- 获取验证码按钮
- 验证码输入框
- 登录按钮
- 返回按钮
- 错误提示

---

## 5.3 主页 / 文件列表页

页面元素：

- TopAppBar
  - 标题
  - 当前路径
  - 刷新按钮
- 文件列表
- 空状态
- 错误状态
- Loading
- 管理员 FAB：
  - 上传
  - 新建目录

---

## 5.4 文件详情/下载面板

展示内容：

- 文件名
- 文件大小
- 修改时间
- 文件路径
- 下载按钮
- 下载状态

管理员可见：

- 删除
- 重命名

---

## 5.5 下载任务页

页面元素：

- 下载中任务
- 已完成任务
- 失败任务
- 下载进度
- 当前下载速度
- 暂停/继续，可选
- 取消下载
- 重试
- 打开文件

本期最低要求：

- 下载中
- 已完成
- 失败重试
- 取消下载

---

## 5.6 设置页

页面元素：

- 深色模式设置
- 在线公告
- 清除下载缓存，可选
- 关于
- 退出登录

---

## 5.7 个人中心页

页面元素：

- 头像
- GitHub 登录显示 GitHub 头像；纯数字 QQ 邮箱登录显示 QQ 头像，失败时回退默认头像
- GitHub 显示 GitHub 昵称；纯数字 QQ 邮箱尝试获取 QQ 昵称，失败时使用邮箱前缀
- 用户名
- 邮箱
- 角色标签
- 登录方式
- 退出登录

---

## 5.8 更新弹窗

内容：

- 当前版本
- 最新版本
- 更新日志
- 强制更新标识
- 立即更新
- 取消按钮，非强制更新时可取消

---

# 6. UI / UX 规范

## 6.1 设计风格

- Material 3 Expressive
- 大圆角卡片
- 清晰的层级关系
- 柔和的阴影与动效
- 支持动态颜色，可选
- 支持深色模式

---

## 6.2 页面状态规范

所有主要页面必须支持以下状态：

- Loading
- Success
- Empty
- Error
- Unauthorized

---

## 6.3 组件规范

### 列表项

文件列表项展示：

- 图标
- 文件名
- 文件大小或子项数量
- 修改时间

### 操作反馈

使用：

- Snackbar
- Toast，少量场景
- Dialog
- ProgressDialog 或 Loading Overlay

### 管理员操作入口

- 普通用户不可见管理操作
- 管理员通过 FAB 或长按菜单触发

---

## 6.4 深色模式设计规范

### 主题模式

支持三种模式：

```kotlin
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
```

默认值：`SYSTEM`

---

### 存储位置

主题模式、自动取色和备用主题色保存在 DataStore：

```text
settings.theme_mode = SYSTEM | LIGHT | DARK
settings.dynamic_color_enabled = true | false
settings.seed_color_argb = opaque ARGB Int
settings.theme_scheme_variant = TONAL_SPOT | FIDELITY | MONOCHROME | NEUTRAL | VIBRANT | EXPRESSIVE | CONTENT | RAINBOW
```

---

### 切换逻辑

```kotlin
when (themeMode) {
    ThemeMode.SYSTEM -> 跟随系统
    ThemeMode.LIGHT -> 强制浅色
    ThemeMode.DARK -> 强制深色
}

Android 12 及以上且 `dynamic_color_enabled=true` 时使用系统莫奈配色；关闭后根据 `seed_color_argb` 和 `theme_scheme_variant` 通过 Material Color Utilities HCT 算法生成完整浅色/深色角色。设置页提供预设色、恢复默认和自定义种子色，并使用官方英文名提供 `Tonal Spot`、`Fidelity`、`Monochrome`、`Neutral`、`Vibrant`、`Expressive`、`Content`、`Rainbow` 八种方案。Android 11 及以下直接使用该种子色方案。
```

---

### Compose 应用方式

```kotlin
val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

MaterialTheme(
    colorScheme = if (darkTheme) darkColorScheme else lightColorScheme,
    typography = AppTypography,
    shapes = AppShapes
) {
    AppNavHost()
}
```

---

### 深色模式适配要求

- 文本对比度清晰
- 卡片背景不能过亮
- 图标颜色适配暗色
- 状态栏与导航栏颜色适配
- 图片与文件图标避免刺眼
- 弹窗、底部面板、Snackbar 均需适配

---

# 7. 技术架构

## 7.1 技术选型

| 模块 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 设计规范 | Material 3 Expressive |
| 架构模式 | MVVM + Repository |
| 导航 | Navigation Compose |
| 异步 | Coroutines + Flow |
| 网络 | OkHttp + Retrofit |
| 依赖注入 | Hilt |
| 本地存储 | DataStore |
| 安全存储 | EncryptedSharedPreferences / Keystore |
| 图片加载 | Coil |
| 下载服务 | Foreground Service |
| 崩溃日志 | Timber + Crashlytics/Sentry，可选 |

---

## 7.2 模块划分

```text
app/
├── core/
│   ├── network/
│   ├── webdav/
│   ├── security/
│   ├── storage/
│   ├── theme/
│   └── common/
├── data/
│   ├── auth/
│   ├── file/
│   ├── download/
│   └── update/
├── domain/
│   ├── auth/
│   ├── file/
│   ├── download/
│   └── update/
├── feature/
│   ├── auth/
│   ├── files/
│   ├── download/
│   ├── settings/
│   ├── profile/
│   └── update/
└── di/
```

---

# 8. 网络层设计

## 8.1 基础要求

- 全部使用 HTTPS
- 统一超时时间
- 统一错误处理
- 支持 token 自动刷新
- Debug 可输出日志，Release 禁止输出敏感信息

---

## 8.2 OkHttp 拦截器

### AuthInterceptor

作用：

- 自动附加 Authorization header

### TokenRefreshInterceptor

作用：

- 遇到 401 时尝试刷新 token
- 刷新成功重放请求
- 刷新失败跳转登录页

### LoggingInterceptor

作用：

- 仅 Debug 环境启用
- 不打印 token、密码、验证码、加密 URL 明文

---

## 8.3 统一错误模型

```kotlin
sealed class AppError {
    data object Network : AppError()
    data object Unauthorized : AppError()
    data object Forbidden : AppError()
    data object NotFound : AppError()
    data object Timeout : AppError()
    data class Server(val code: Int, val message: String) : AppError()
    data object Unknown : AppError()
}
```

---

# 9. 登录模块设计

## 9.1 登录方式

### GitHub OAuth

要求：

- 使用 PKCE
- 不在客户端保存 client_secret
- 由后端完成 code 换 token
- 后端读取 GitHub 邮箱并判断角色

### Email 登录

要求：

- 使用邮箱验证码登录
- 验证码有效期 5 分钟
- 同一邮箱发送频率限制
- 登录失败次数限制

---

## 9.2 用户模型

```kotlin
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val avatarUrl: String?
)

enum class Role {
    USER,
    ADMIN
}
```

---

## 9.3 token 存储

存储内容：

- accessToken
- refreshToken
- token 过期时间

存储方式：

- EncryptedSharedPreferences
- 或 Keystore 加密方案

禁止：

- 明文存储
- 写入普通日志
- 写入崩溃日志

---

# 10. 文件列表模块设计

## 10.1 文件模型

```kotlin
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long?,
    val lastModified: Long?,
    val mimeType: String?,
    val etag: String?
)
```

---

## 10.2 WebDAV 操作定义

| 操作 | 方法 |
|---|---|
| 获取目录列表 | PROPFIND |
| 下载文件 | GET |
| 上传文件 | PUT |
| 新建目录 | MKCOL |
| 删除 | DELETE |
| 重命名/移动 | MOVE |
| 复制 | COPY |

---

## 10.3 WebDAV 客户端接口

```kotlin
interface WebDavClient {
    suspend fun list(path: String): List<FileNode>

    suspend fun download(
        path: String,
        targetFile: File,
        startBytes: Long = 0L,
        onProgress: (downloaded: Long, total: Long?) -> Unit
    ): DownloadResult

    suspend fun upload(
        path: String,
        file: File,
        onProgress: (uploaded: Long, total: Long) -> Unit
    )

    suspend fun mkdir(path: String)

    suspend fun delete(path: String)

    suspend fun rename(fromPath: String, newName: String)
}
```

---

## 10.4 文件列表要求

- 支持目录层级导航
- 支持返回上一级
- 支持下拉刷新
- 支持空目录提示
- 支持错误重试
- 管理员与普通用户看到不同操作入口

---

# 11. 下载模块设计

## 11.1 下载任务模型

```kotlin
data class DownloadTask(
    val id: String,
    val fileName: String,
    val remotePath: String,
    val downloadUrl: String?,
    val localPath: String,
    val tempPath: String,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val supportRange: Boolean,
    val etag: String?,
    val lastModified: String?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class DownloadStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED,
    CANCELLED
}
```

---

## 11.2 下载方式

本期采用：

- Foreground Service
- 通知栏显示下载进度

原因：

- 下载任务较长时间运行
- Android 8.1 及以上后台限制较严格
- 前台服务稳定性更好

---

## 11.3 通知栏要求

### Android 8.1 兼容

- 必须创建 NotificationChannel

### 通知内容

- 文件名
- 下载进度
- 下载速度，可选
- 取消按钮
- 下载完成后可点击打开文件

---

## 11.4 断点续传设计

### 基本原理

1. 下载时先保存为临时文件：
   ```text
   filename.ext.part
   ```

2. 记录已下载字节数

3. 恢复下载时发送：
   ```http
   Range: bytes=已下载字节数-
   ```

4. 服务端返回 206 Partial Content，则继续下载

5. 下载完成后将 `.part` 文件重命名为正式文件

---

### 服务端能力判断

下载前先判断：

- `Accept-Ranges: bytes`
- 请求 Range 后是否返回 206
- ETag 或 Last-Modified 是否一致

---

### 断点续传规则

| 情况 | 处理方式 |
|---|---|
| 服务端支持 Range | 继续下载 |
| 服务端不支持 Range | 重新下载 |
| 临时文件不存在 | 重新下载 |
| ETag 不一致 | 删除临时文件，重新下载 |
| 文件大小异常 | 重新下载 |
| 下载中断 | 恢复时尝试续传 |
| 用户取消 | 删除临时文件或保留，本期建议删除 |

---

### 下载状态机

```text
PENDING
  ↓
RUNNING
  ↓
SUCCESS / FAILED / CANCELLED
```

如果支持暂停：

```text
RUNNING <-> PAUSED
```

本期最低要求：

- 支持继续下载
- 支持取消下载
- 支持失败重试

暂停/继续可作为可选项。

---

## 11.5 下载流程伪代码

```kotlin
fun download(task: DownloadTask) {
    val tempFile = File(task.tempPath)
    var startBytes = 0L

    if (tempFile.exists() && task.supportRange) {
        startBytes = tempFile.length()
    }

    val request = buildDownloadRequest(
        url = task.downloadUrl,
        range = startBytes
    )

    executeDownload(request, tempFile, startBytes)

    if (downloadSuccess) {
        tempFile.renameTo(finalFile)
        updateTaskSuccess()
        showSuccessNotification()
    }
}
```

---

## 11.6 下载失败处理

失败场景：

- 网络中断
- 服务端异常
- 存储空间不足
- 文件写入失败
- token 过期
- URL 过期
- 服务端不支持 Range 且临时文件损坏

处理策略：

- 自动重试 1～3 次
- 保留任务状态
- 提示用户手动重试
- token 过期时刷新 token
- URL 过期时重新请求下载地址

---

# 12. 管理员读写模块设计

## 12.1 上传文件

### 功能要求

- 管理员可见
- 支持选择本地文件
- 显示上传进度
- 上传成功后刷新列表
- 上传失败提示并支持重试

### 上传限制

- 单文件大小限制，建议可配置
- 禁止非法文件名
- 禁止路径穿越
- 同名文件默认提示冲突

---

## 12.2 新建目录

### 输入校验

禁止包含：

```text
/
\
..
```

目录名长度建议：

```text
1 - 100 字符
```

---

## 12.3 删除

### 要求

- 删除前必须二次确认
- 删除目录需提示将删除目录内容
- 删除成功后刷新列表

---

## 12.4 重命名

### 要求

- 支持文件重命名
- 支持目录重命名
- 不允许重名为当前目录已存在名称
- 不允许非法字符

---

# 13. 加密更新 URL 模块设计

## 13.1 更新信息模型

```kotlin
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val forceUpdate: Boolean,
    val encryptedUrl: String,
    val changelog: String,
    val expiresAt: Long,
    val signature: String?
)
```

---

## 13.2 服务端返回示例

```json
{
  "versionCode": 12,
  "versionName": "1.2.0",
  "forceUpdate": false,
  "encryptedUrl": "xxxx",
  "changelog": "修复若干问题",
  "expiresAt": 1730000000,
  "signature": "xxxx"
}
```

---

## 13.3 URL 解析方式

推荐方式：

1. App 请求更新信息
2. App 获取 encryptedUrl
3. App 请求服务端解析
4. 服务端校验用户权限、过期时间、签名
5. 服务端返回短期可下载地址

### 解析接口

```http
POST /api/v1/update/resolve
```

Request：

```json
{
  "encryptedUrl": "xxxx"
}
```

Response：

```json
{
  "url": "https://cdn.example.com/app/release.apk",
  "expiresIn": 300
}
```

---

## 13.4 安全要求

- 客户端不内置长期解密密钥
- 加密 URL 必须有时效
- 加密 URL 必须可校验签名
- 过期 URL 不可使用
- 普通用户和管理员按权限区分

---

# 14. App 版本更新模块设计

## 14.1 更新检查时机

- 启动时检查
- 设置页手动检查

---

## 14.2 更新规则

### 强制更新

- 弹窗不可取消
- 必须下载并安装后才能继续使用

### 可选更新

- 弹窗可取消
- 用户可稍后更新

---

## 14.3 APK 下载

- 使用统一下载模块
- 支持断点续传
- 通知栏显示进度
- 下载完成后提示安装

---

## 14.4 安装权限

Android 8.1 安装 APK 需要：

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

同时需要引导用户开启“允许安装未知来源应用”。

---

## 14.5 FileProvider

下载完成后通过 FileProvider 提供 APK 访问：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

---

# 15. 深色模式模块设计

## 15.1 功能入口

设置页：

```text
外观设置
- 跟随系统
- 浅色模式
- 深色模式
- 莫奈自动取色
- 主题色预设 / 自定义 / 官方配色方案
```

---

## 15.2 状态管理

```kotlin
data object ThemeRepository {
    val themeSettings: Flow<ThemeSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColorEnabled(enabled: Boolean)
    suspend fun setSeedColor(argb: Int)
    suspend fun setSchemeVariant(variant: ThemeSchemeVariant)
}
```

---

## 15.3 DataStore 存储字段

```text
theme_mode: SYSTEM / LIGHT / DARK
dynamic_color_enabled: true / false
seed_color_argb: opaque ARGB Int
theme_scheme_variant: TONAL_SPOT / FIDELITY / MONOCHROME / NEUTRAL / VIBRANT / EXPRESSIVE / CONTENT / RAINBOW
```

---

## 15.4 主题应用

```kotlin
@Composable
fun AppTheme(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    seedColorArgb: Int,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (dynamicColorEnabled && SDK_INT >= 31) {
            systemDynamicColorScheme
        } else {
            hctColorScheme(seedColorArgb, darkTheme)
        },
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
```

---

## 15.5 深色模式验收标准

- 设置切换后立即生效
- 重启 App 后保持用户选择
- 深色模式下文字可读
- 弹窗、列表、按钮、输入框无颜色异常
- 状态栏图标颜色正确
- 自动取色开关和自定义主题色在重启后保持
- 自定义浅色与深色方案的文本和控件对比度合格

---

# 16. Android 8.1 兼容设计

## 16.1 minSdk

```gradle
minSdk = 27
```

即 Android 8.1。

---

## 16.2 通知渠道

Android 8.0 起必须使用 NotificationChannel。

下载通知需要创建渠道：

```text
Channel ID: download_channel
Channel Name: 文件下载
```

---

## 16.3 存储权限

### Android 8.1 / 9

需要运行时申请：

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

### Android 10+

建议优先使用：

- 应用私有目录
- MediaStore
- SAF，可选

本期为了稳定实现，建议默认下载到：

```text
应用私有下载目录
```

或用户通过 SAF 选择目录。

---

## 16.4 前台服务

如果使用前台下载服务：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

如果 targetSdk 较高，还需要适配：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

---

## 16.5 通知权限

Android 13+ 需要：

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

并运行时申请。

---

# 17. 权限清单

## 17.1 AndroidManifest 权限建议

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
```

---

# 18. 后端接口设计

> 说明：即使客户端直连 WebDAV，也建议有一个最小后端负责登录、角色、配置和加密更新 URL。

---

## 18.1 GitHub 登录

```http
POST /api/v1/auth/github
```

Request：

```json
{
  "code": "github_oauth_code",
  "redirectUri": "https://example.com/callback",
  "deviceId": "android_device_id"
}
```

Response：

```json
{
  "accessToken": "xxx",
  "refreshToken": "xxx",
  "expiresIn": 3600,
  "user": {
    "id": "u_123",
    "name": "用户",
    "email": "user@qq.com",
    "role": "USER",
    "avatarUrl": "https://..."
  }
}
```

---

## 18.2 发送邮箱验证码

```http
POST /api/v1/auth/email/code
```

Request：

```json
{
  "email": "user@qq.com"
}
```

Response：

```json
{
  "success": true,
  "expiresIn": 300
}
```

---

## 18.3 邮箱验证码登录

```http
POST /api/v1/auth/email/login
```

Request：

```json
{
  "email": "user@qq.com",
  "code": "123456",
  "deviceId": "android_device_id"
}
```

Response：

```json
{
  "accessToken": "xxx",
  "refreshToken": "xxx",
  "expiresIn": 3600,
  "user": {
    "id": "u_456",
    "name": "管理员",
    "email": "admin@mczihan.link",
    "role": "ADMIN",
    "avatarUrl": null
  }
}
```

---

## 18.4 刷新 token

```http
POST /api/v1/auth/refresh
```

Request：

```json
{
  "refreshToken": "xxx"
}
```

---

## 18.5 登出

```http
POST /api/v1/auth/logout
```

---

## 18.6 获取 WebDAV 配置

```http
GET /api/v1/webdav/config
```

Response：

```json
{
  "baseUrl": "https://dav.example.com",
  "homePath": "/",
  "credentialType": "bearer",
  "accessToken": "short_lived_token",
  "permission": "read_write",
  "expiresAt": 1730000000
}
```

说明：

- 普通用户：`permission = read_only`
- 管理员：`permission = read_write`

---

## 18.7 获取更新信息

```http
GET /api/v1/update/info
```

Response：

```json
{
  "versionCode": 12,
  "versionName": "1.2.0",
  "forceUpdate": false,
  "encryptedUrl": "xxxx",
  "changelog": "修复若干问题",
  "expiresAt": 1730000000,
  "signature": "xxx"
}
```

---

## 18.8 解析加密更新 URL

```http
POST /api/v1/update/resolve
```

Request：

```json
{
  "encryptedUrl": "xxx"
}
```

Response：

```json
{
  "url": "https://cdn.example.com/app/release.apk",
  "expiresIn": 300
}
```

---

# 19. 错误码设计

| code | message | 说明 |
|---|---|---|
| 10001 | unauthorized | 未登录 |
| 10002 | token_expired | token 过期 |
| 10003 | forbidden | 权限不足 |
| 10004 | email_domain_not_allowed | 邮箱域名不允许 |
| 20001 | file_not_found | 文件不存在 |
| 20002 | path_invalid | 路径非法 |
| 20003 | file_conflict | 文件冲突 |
| 20004 | upload_failed | 上传失败 |
| 20005 | download_failed | 下载失败 |
| 30001 | update_url_expired | 更新链接过期 |
| 30002 | update_url_invalid | 更新链接无效 |
| 40001 | network_error | 网络错误 |
| 40002 | timeout | 请求超时 |

---

# 20. 安全要求

## 20.1 认证安全

- 不在客户端保存 GitHub client_secret
- Email 验证码需要防爆破
- token 必须加密存储
- 登出后清除本地敏感数据

---

## 20.2 权限安全

- 客户端角色只用于 UI 控制
- 真正权限必须由服务端或 WebDAV 服务端控制
- 普通用户不能通过修改本地角色获得写权限

---

## 20.3 WebDAV 安全

- 使用 HTTPS
- 不使用长期管理员账号密码硬编码
- 管理员写权限应通过短期凭据或代理实现
- 禁止路径穿越
- 删除操作需要确认

---

## 20.4 下载安全

- 下载链接应有时效
- APK 下载建议校验 SHA-256，可选
- 安装 APK 前提示用户

---

# 21. 测试要求

## 21.1 登录测试

- GitHub 登录成功
- Email 登录成功
- `@qq.com` 登录为普通用户
- `@mczihan.link` 登录为管理员
- 其他邮箱拒绝登录
- token 过期自动刷新
- 登出后无法访问主页

---

## 21.2 文件列表测试

- 根目录加载成功
- 子目录进入成功
- 返回上一级正常
- 空目录显示空态
- 网络失败显示错误态
- 刷新正常

---

## 21.3 下载测试

- 小文件下载成功
- 大文件下载成功
- 下载进度正确
- 通知栏进度正确
- 下载完成通知可点击
- 取消下载正常
- 网络中断后重试正常

---

## 21.4 断点续传测试

- 暂停或中断后恢复下载
- 服务端支持 Range 时继续下载
- 服务端不支持 Range 时重新下载
- 临时文件损坏时重新下载
- 下载完成后文件可打开

---

## 21.5 管理员测试

- 普通用户不可见上传入口
- 普通用户不可见删除入口
- 管理员上传成功
- 管理员新建目录成功
- 管理员删除成功
- 管理员重命名成功

---

## 21.6 深色模式测试

- 跟随系统正常
- 手动浅色正常
- 手动深色正常
- 重启后保持设置
- 所有页面深色模式无显示异常

---

## 21.7 兼容性测试

重点测试：

- Android 8.1
- Android 9
- Android 10
- Android 11
- Android 12
- Android 13
- Android 14+

重点关注：

- 存储权限
- 通知权限
- 前台服务
- 深色模式
- 安装 APK
- 网络 TLS 兼容性

---

# 22. 验收标准

## 22.1 登录模块

- GitHub 登录可用
- Email 验证码登录可用
- 邮箱域名角色判断正确
- 非法邮箱无法登录
- 登录状态可持久化
- 退出登录正常

---

## 22.2 文件模块

- 普通用户可查看文件列表
- 普通用户可下载文件
- 管理员可上传文件
- 管理员可新建目录
- 管理员可删除文件/目录
- 管理员可重命名文件/目录

---

## 22.3 下载模块

- 下载进度准确
- 通知栏显示正常
- 下载完成可打开文件
- 失败可重试
- 断点续传在服务端支持时生效

---

## 22.4 更新模块

- 可获取最新版本信息
- 强制更新逻辑正确
- 非强制更新可取消
- 加密 URL 过期时提示错误
- APK 下载完成后可安装

---

## 22.5 深色模式

- 三种主题模式均可切换
- 设置持久化
- 切换后界面立即生效
- 深色模式 UI 正常

---

# 23. 开发任务拆分

## 阶段一：项目初始化

- 创建 Android 项目
- 配置 Kotlin
- 配置 Compose
- 配置 Material 3
- 配置 Hilt
- 配置 Navigation
- 搭建基础目录结构
- 接入 Timber
- 配置主题与深色模式基础框架

---

## 阶段二：登录模块

- 登录页 UI
- Email 登录页 UI
- GitHub OAuth 接入
- Email 验证码登录接入
- token 存储
- 用户信息存储
- 登录状态管理
- 退出登录

---

## 阶段三：文件列表模块

- WebDAV 客户端封装
- PROPFIND 解析
- 文件列表 UI
- 目录导航
- 刷新
- 空态/错误态
- 文件详情面板

---

## 阶段四：下载模块与断点续传

- 下载任务模型
- 下载服务
- 通知栏进度
- 临时文件管理
- Range 请求
- 断点续传逻辑
- 下载失败重试
- 下载完成打开文件

---

## 阶段五：管理员功能

- 管理员权限判断
- 上传文件
- 上传进度
- 新建目录
- 删除文件/目录
- 重命名
- 操作确认弹窗

---

## 阶段六：更新模块

- 获取更新信息
- 加密 URL 解析
- 更新弹窗
- APK 下载
- APK 安装
- 强制更新逻辑

---

## 阶段七：设置与深色模式

- 设置页
- 主题切换
- DataStore 持久化
- 深色模式适配
- 关于页
- 清除缓存，可选

---

## 阶段八：兼容与测试

- Android 8.1 适配
- 存储权限适配
- 通知权限适配
- 前台服务适配
- 错误处理完善
- 全流程测试
- 发版准备

---

# 24. 风险点

## 24.1 WebDAV 兼容性风险

不同 WebDAV 服务端对以下方法支持可能不同：

- PROPFIND
- MOVE
- MKCOL
- Range 下载

需要提前用目标 WebDAV 服务测试。

---

## 24.2 Android 8.1 兼容风险

重点风险：

- 存储权限
- 通知渠道
- 前台服务
- APK 安装
- TLS 兼容性

---

## 24.3 下载稳定性风险

大文件下载可能遇到：

- 网络中断
- 进程被杀
- 存储空间不足
- 文件写入异常

必须做重试与状态恢复。

---

## 24.4 安全风险

如果客户端直接保存 WebDAV 管理员账号密码，会有严重安全风险。

建议：

- 后端签发短期凭据
- 或使用 WebDAV 代理
- 或按角色隔离目录

---

# 25. 最终本期 MVP 功能清单

本期必须完成：

- 登录页
- GitHub OAuth 登录
- Email 验证码登录
- 用户角色识别
- 文件列表
- 文件下载
- 下载进度与通知
- 断点续传
- 管理员上传
- 管理员新建目录
- 管理员删除
- 管理员重命名
- 加密更新 URL 获取与解析
- App 版本更新
- 设置页
- 个人中心
- 退出登录
- 深色模式
- 统一错误处理
- Android 8.1 兼容适配
