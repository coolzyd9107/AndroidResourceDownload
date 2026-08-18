# Android Resource Download

这是一个面向 Android 的 WebDAV 文件客户端项目，计划提供受控登录、按角色浏览文件、可靠下载、管理员文件管理以及应用更新能力。产品与技术范围以 [Dev.md](Dev.md) 为准。

## 当前状态

**Phase 1 scaffold / mock UI, not production-ready（阶段一脚手架 / Mock UI，尚不可用于生产环境）。**

当前仓库处于初始化阶段。真实 GitHub OAuth、邮箱验证码认证、WebDAV 读写、文件下载与断点续传、远程更新 URL 解析和 APK 更新尚未宣称完成。首轮实现先使用 Mock 后端建立可测试的页面、状态模型和导航，再按实施计划接入真实服务。

## 目标技术栈

- Kotlin
- Jetpack Compose 与 Material 3
- MVVM + Repository
- Navigation Compose
- Coroutines + Flow
- OkHttp + Retrofit
- Hilt
- DataStore，以及 Android Keystore 支持的敏感信息存储
- 前台服务与系统通知，用于后续下载任务

具体依赖版本由项目 Gradle 配置统一管理；本文不预先声明未经验证的版本。

## Android 支持范围

- 最低支持 Android 8.1（API 27）。
- 后续兼容性验证覆盖新版 Android 的通知权限、存储策略、前台服务与 APK 安装限制。
- “支持”表示设计目标；各版本的完整验证将在阶段八完成。

## GitHub Actions 构建

项目使用名为 **Android CI** 的 GitHub Actions 工作流进行远程编译。工作流在推送到 `main`、面向 `main` 的 Pull Request 以及手动触发时运行，并执行：

```text
./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug
```

CI 仅生成使用 Android 默认 Debug 签名的调试包，不配置发布签名，不读取发布密钥或签名 Secrets。任务成功后，可进入 GitHub 仓库的 **Actions** 页面，打开对应的 **Android CI** 运行，在 **Artifacts** 区域下载 `android-debug-apk`；其中包含 `app-debug.apk`。

远程 CI 是项目的编译与合并门禁，本工作流不要求本地编译。Android 项目脚手架、`gradlew` 和 `app` 模块提交后，工作流才具备实际构建输入。

## 可选的本地环境

本地开发不是获取 Debug APK 的前提；需要修改或调试 Android 代码时，可准备：

- JDK 17
- Android SDK，以及项目 Gradle 配置要求的平台与构建工具
- Android Studio（可选）

应使用仓库提供的 Gradle Wrapper，避免依赖系统全局安装的 Gradle。

## 仓库结构

```text
.
├── .github/workflows/android-ci.yml   # 远程 Debug CI
├── app/                               # Android 应用模块与 Mock UI
├── docs/IMPLEMENTATION_PLAN.md        # 八阶段实施与验收计划
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
