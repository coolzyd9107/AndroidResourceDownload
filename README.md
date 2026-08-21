# Android Resource Download

这是一个面向 Android 的 WebDAV 文件客户端项目，当前按 v1.1 追加方案建设：Android 登录后获取后端下发的角色凭据，直接连接 WebDAV 完成列表、下载和管理员写操作。产品与技术范围以 [Dev.md](Dev.md) 与 [FRONTEND_APPEND_V1_1.md](docs/FRONTEND_APPEND_V1_1.md) 为准。

## 当前状态

**Phase 3 read-only browsing / v1.1 direct WebDAV integration in progress（阶段三只读浏览 / v1.1 直连 WebDAV 接入中）。**

首版 Mock UI 已保留为显式 Demo 模式。真实模式已接入 GitHub OAuth/邮箱认证、加密会话恢复、内存 WebDAV 凭据、目录浏览、受支持文本与图片的内存预览、下载队列、仓库版本检查、可开关的莫奈自动取色与自定义 HCT 主题，以及管理员专属的三并发上传队列、递归文件夹上传、重命名、移动、复制和删除操作。

## 目标技术栈

- Kotlin
- Jetpack Compose 与 Material 3
- MVVM + Repository
- Navigation Compose
- Coroutines + Flow
- OkHttp + Retrofit
- Hilt
- DataStore，以及 Android Keystore 支持的敏感信息存储
- 前台服务与系统通知，用于下载和上传任务

具体依赖版本由项目 Gradle 配置统一管理；本文不预先声明未经验证的版本。

## Android 支持范围

- 最低支持 Android 8.1（API 27）。
- 后续兼容性验证覆盖新版 Android 的通知权限、存储策略、前台服务与 APK 安装限制。
- “支持”表示设计目标；各版本的完整验证将在阶段八完成。

## GitHub Actions 构建

项目使用名为 **Android CI** 的 GitHub Actions 工作流进行远程编译。工作流在推送到 `main`、面向 `main` 的 Pull Request 以及手动触发时运行。Android 构建从 Actions Variable `API_BASE_URL` 注入真实 HTTPS 后端地址，并以 `demoMode=false` 执行 Release 检查：

```text
./gradlew --no-daemon lintRelease testReleaseUnitTest assembleRelease
```

所有事件先执行 `lintRelease`、`testReleaseUnitTest` 和无签名的 `assembleRelease`，这一步不接触发布密钥。推送到 `main` 或在 `main` 手动触发时，独立的签名 Job 进入受保护的 `release` Environment，从以下 Environment Secrets 读取发布签名并生成可安装的 `app-release.apk`：

- `ANDROID_KEYSTORE_BASE64`：keystore 文件的 Base64 内容。
- `ANDROID_KEYSTORE_PASSWORD`：keystore 密码。
- `ANDROID_KEY_ALIAS`：发布密钥别名。
- `ANDROID_KEY_PASSWORD`：发布密钥密码。

`release` Environment 应将部署分支限制为 `main`，并按需要启用审批。任务成功后，可在对应 **Android CI** 运行的 **Artifacts** 区域下载 `android-real-release-apk`。keystore 文件只在 Runner 临时目录中解码，并在构建结束后删除；仓库不保存发布密钥。

本地签名构建使用 `ANDROID_KEYSTORE_PATH` 指向 keystore 文件，并同时设置 `ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS` 和 `ANDROID_KEY_PASSWORD`；`ANDROID_KEYSTORE_BASE64` 仅供 CI 解码使用。

远程 CI 是项目的编译与合并门禁，本工作流不要求本地编译。Android 项目脚手架、`gradlew` 和 `app` 模块提交后，工作流才具备实际构建输入。

## 可选的本地环境

本地开发不是获取 Release APK 的前提；需要修改或调试 Android 代码时，可准备：

- JDK 17
- Android SDK，以及项目 Gradle 配置要求的平台与构建工具
- Android Studio（可选）

应使用仓库提供的 Gradle Wrapper，避免依赖系统全局安装的 Gradle。

## 仓库结构

```text
.
├── .github/workflows/android-ci.yml   # 远程 Release CI
├── app/                               # Android 应用模块与 Mock UI
├── docs/FRONTEND_APPEND_V1_1.md      # v1.1 Android 直连 WebDAV 契约
├── backend/                           # Go 认证、凭据和更新 API
├── gradle/libs.versions.toml          # Gradle 版本目录
├── build.gradle.kts                   # 项目级构建配置
├── settings.gradle.kts                # 项目与模块设置
├── Dev.md                             # 产品、架构和功能开发文档
└── README.md                          # 项目入口说明
```

阶段一已包含 Gradle Wrapper、应用源码、Mock UI、基础单元测试与远程 CI。当前目录按 `core`、`data`、`domain`、`feature` 等职责组织；真实认证、WebDAV、下载与更新能力仍按后续阶段接入。

## 开发路线

- 完整产品需求、权限规则、接口草案与技术设计：[Dev.md](Dev.md)
- 当前阶段、各阶段交付物、验证方式与风险：[docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md)
