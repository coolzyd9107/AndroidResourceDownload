# Android v1.1 追加实现说明

本文记录 Android 端追加方案的当前落地边界，配套产品原始需求见 `Dev.md`。

## 真实契约

Android 使用后端实际路由和统一响应包络：

- `GET /api/v1/auth/github/start`
- `GET /api/v1/auth/github/callback`
- `POST /api/v1/auth/github/complete`
- `POST /api/v1/auth/email/code`
- `POST /api/v1/auth/email/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/webdav/credential`
- `GET /api/v1/update/info`
- `POST /api/v1/update/resolve`

响应形态为 `{ "code": 0, "message": "ok", "data": ... }`。GitHub 登录不检查邮箱后缀，角色以后端 `role` 为准；邮箱验证码登录仍只允许 `@qq.com` 和 `@mczihan.link`。

## 安全边界

本期使用 HTTPS 简化凭据模式，因为现有后端会返回明文 JSON 凭据，且尚未实现真正的 ECDH 协议。Android 将 WebDAV 用户名和密码限制在进程内存中，过期、401、角色变化和登出时清除；不写入 Room、DataStore、普通文件或日志。`expiresAt` 不能撤销后端当前的静态共享 WebDAV 密码，生产环境仍应换成服务端可撤销的临时账号、令牌或代理。

ECDH-ES/A256GCM 不会在客户端单方面伪实现，也不会把长期密钥内置 APK。后续增强协议必须同时具备版本、服务端签名、Keystore 私钥、HKDF 域隔离、AEAD 关联数据和禁止降级策略。

## 构建配置

真实模式通过未提交的 Gradle 属性注入：

```properties
apiBaseUrl=https://api.example.com/
demoMode=false
```

仓库不保存真实后端地址、GitHub Client Secret 或 WebDAV 密码。Debug 默认使用显式 Demo 模式；真实网络错误不会自动显示 Mock 数据。

## 仓库版本检查

设置页手动读取仓库根目录 `latest_version.txt`，格式为 `latest_version=x.x.x` 和 `update_url=https://...`。客户端使用 `BuildConfig.VERSION_NAME` 的完整三段数字版本逐段比较，不使用 `versionCode`；仅当远端版本更高时显示下载提示，并通过系统浏览器打开经过 HTTPS 校验的更新地址。本地版本大于或等于远端版本时只提示已是最新版本。

## Android 直连 WebDAV

凭据拿到后，Android 使用独立的 OkHttp WebDAV 客户端直接执行：

- `PROPFIND`：目录列表，`Depth: 1`，流式 XML 解析
- `HEAD`：长度、ETag、Last-Modified、Range 能力
- `GET`：普通和 Range 下载，校验 `206` 的 `Content-Range`
- `PUT`、`MKCOL`、`DELETE`、`MOVE`、`COPY`：仅 `READ_WRITE` 凭据允许构造请求

Basic Auth 按请求附加，不使用全局拦截器；跨源重定向被禁止。所有路径以解码后的安全 segment 表示，拒绝 `.`、`..`、斜线、反斜线、控制字符、编码分隔符和越过 credential root 的 href。

WebDAV `401` 只允许一次：失效当前 credential generation，单飞重新获取，重建请求后重试；第二次仍为 401 即返回未授权错误，禁止循环。

## 后续阶段

GitHub OAuth callback 由后端 HTTPS 接收，后端只通过 App deep link 返回短期一次性 code；App 以 PKCE verifier 兑换会话 token，随后再请求短期 WebDAV 凭据。URL 中禁止放 access/refresh token、WebDAV 地址、账号或密码。

Room 现在仅持久化任务所有者、远程安全路径、进度、状态和续传校验信息；WebDAV 地址、用户名、密码及短期解析 URL 均不进入数据库。前台服务按用户串行执行队列，文件先写入应用内部目录的 `.part`，仅在强 ETag 或原始 Last-Modified 匹配且 `206 Content-Range` 有效时追加，完成后通过 FileProvider 打开。

管理员账号可通过系统文件选择器上传，可输入名称新建文件夹，并可移动、复制、删除云端文件或目录。移动和复制通过云端目录选择器确定目标位置并保留原名称，不再要求手工输入完整路径。上传先写入同目录唯一临时名，完成后用 `MOVE` 提交；默认禁止覆盖，冲突后必须由管理员明确确认。普通用户不显示写操作，READ_ONLY 凭据也会在网络请求构造前拒绝写入。

支持的文件可通过凭据化 WebDAV `GET` 直接在内存中预览，不创建下载任务、不写入文件或缓存。纯文本、常见代码/配置、RTF、DOCX/ODT 文本内容以及 JPEG、PNG、WebP、BMP、静态 GIF 受支持；旧版二进制 DOC、PDF、SVG、APK 和压缩包不显示预览入口。文本使用有限前缀，图片与文档使用完整编码大小上限；Range 被忽略时仍由流读取边界兜底，Office 容器同时限制 ZIP 解压量和 XML 复杂度。

管理员可在线编辑完整加载的纯文本预览。保存使用保留原编码和 BOM 的 WebDAV `PUT`，并且仅在本次预览 GET 返回有效强 ETag 时开放编辑，通过 `If-Match` 防止覆盖并发修改。普通用户、截断文本、未知或无法无损写回的编码、缺少强 ETag 的文件，以及 RTF、DOCX、ODT 和图片均隐藏编辑入口。
