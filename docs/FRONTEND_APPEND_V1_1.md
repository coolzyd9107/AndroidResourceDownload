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

## Android 直连 WebDAV

凭据拿到后，Android 使用独立的 OkHttp WebDAV 客户端直接执行：

- `PROPFIND`：目录列表，`Depth: 1`，流式 XML 解析
- `HEAD`：长度、ETag、Last-Modified、Range 能力
- `GET`：普通和 Range 下载，校验 `206` 的 `Content-Range`
- `PUT`、`MKCOL`、`DELETE`、`MOVE`：仅 `READ_WRITE` 凭据允许构造请求

Basic Auth 按请求附加，不使用全局拦截器；跨源重定向被禁止。所有路径以解码后的安全 segment 表示，拒绝 `.`、`..`、斜线、反斜线、控制字符、编码分隔符和越过 credential root 的 href。

WebDAV `401` 只允许一次：失效当前 credential generation，单飞重新获取，重建请求后重试；第二次仍为 401 即返回未授权错误，禁止循环。

## 后续阶段

GitHub OAuth callback 由后端 HTTPS 接收，后端只通过 App deep link 返回短期一次性 code；App 以 PKCE verifier 兑换会话 token，随后再请求短期 WebDAV 凭据。URL 中禁止放 access/refresh token、WebDAV 地址、账号或密码。SAF 上传、Room 持久下载、前台服务队列和 APK 更新安装仍待实现。
