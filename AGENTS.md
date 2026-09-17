# BV 项目协作指南

## 项目概览

BV 是一个第三方 Bilibili 客户端，支持 Android TV（Jetpack Compose / TV Material3）和 Android Mobile。

- 仓库：`https://github.com/chengoak/bv`
- 项目根目录：`/Users/li/workspace/bv`
- 主要模块：
  - `app`：主应用（包含 `mobile` 与 `tv` 两个 flavor）
  - `app/shared`：共享业务逻辑与 ViewModel
  - `app/tv`：TV 端 UI 与组件
  - `app/mobile`：手机端 UI 与组件
  - `bili-api`：B 站 HTTP API 封装与数据实体
  - `player`：播放器相关模块

## 构建环境

构建前需要设置以下环境变量：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/Users/li/Library/Android/sdk
```

## 常用构建命令

```bash
# 编译 TV 端 Debug Kotlin
./gradlew :app:tv:compileDebugKotlin --no-daemon --offline

# 打包 Release Universal APK（默认渠道）
./gradlew :app:assembleDefaultRelease --no-daemon --offline
```

Release APK 输出路径：

```
app/build/outputs/apk/default/release/BV_<versionCode>_<versionName>.release_default_universal.apk
```

版本号由 `buildSrc/src/main/kotlin/AppConfiguration.kt` 根据 Git 提交数自动生成：

- `versionCode` = `git rev-list --count HEAD`
- `versionName` = `0.3.0.r<versionCode>.<shortCommitHash>.release`

## 发布到 GitHub

1. 确认当前 `versionCode` 与 `versionName`：

```bash
git rev-list --count HEAD
git rev-list HEAD --abbrev-commit --max-count=1
```

2. 构建 Release APK：

```bash
./gradlew :app:assembleDefaultRelease --no-daemon --offline
```

3. 打 Tag 并推送：

```bash
TAG=v0.3.0.r<versionCode>.<shortCommitHash>
git tag "$TAG"
git push origin "$TAG"
```

4. 创建 GitHub Release 并上传 APK：

```bash
gh release create "$TAG" \
  --title "BV $TAG" \
  --notes "更新内容摘要" \
  --repo chengoak/bv \
  app/build/outputs/apk/default/release/BV_<...>.apk
```

> 注意：Release 必须同时上传 APK 附件，不能创建空 Release。

### 实际发布机制（2026-09-17 核实）

- **Release 一律本地手动构建 + `gh release create` 上传，不靠 GitHub Actions。** 历史版本（r918~r925）都是这样发的，Release 资产里的 APK（约 17 MB 的 release 签名包）即本地 `assembleDefaultRelease` 产物，非 CI 产物。
- `.github/workflows/` 下的构建任务在本 fork **不会产出 APK**，原因有二：
  1. 触发分支不匹配：`alpha.yml` 只在 push `develop` 触发，`features.yml` 只在 `feature/**`，`release.yml` 只在 push tag 触发；仅把 commit 推到 `master` 不会触发任何构建。
  2. 每个构建 job 都带 `if: github.repository == 'aaa1115910/bv'`，本仓库是 `chengoak/bv`，条件不满足，即使触发也会被 skip。
- 因此发版标准动作：本地 `assembleDefaultRelease` → 打 **lightweight tag**（`git cat-file -t <tag>` 为 `commit`，与历史一致）并 push → `gh release create <tag> <apk>`。
- Release 签名依赖仓库根目录 `signing.properties`（字段 `keystore.path`/`keystore.pwd`/`keystore.alias`/`keystore.alias_pwd`）+ `key/key.keystore`，二者就位才能签出可覆盖安装的 release 包（v1+v2 签名，与历史版本同 keystore）。
- Debug 包用 `:app:assembleDefaultDebug`（flavor 是 `default`，任务名是 `assembleDefaultDebug`/`assembleDefaultRelease`，没有 `compileMobileDebugKotlin` 这类变体名）。debug APK 约 54 MB，输出在 `app/build/outputs/apk/default/debug/`。

## 模拟器调试（本机）

- 本机没有独立安装 Android Emulator；emulator/adb 来自 homebrew commandline-tools：
  - emulator: `/opt/homebrew/share/android-commandlinetools/emulator/emulator`
  - adb: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
  - AVD 名 `test_device`（android-34，手机镜像 sdk_gphone64_arm64），需 `export ANDROID_AVD_HOME=~/.android/avd`
- 启动后 `adb wait-for-device` 并轮询 `getprop sys.boot_completed` 直到为 1。
- debug 包名 `dev.aaa1115910.bv.debug`，启动 Activity 为 `dev.aaa1115910.bv.launcher.LauncherActivity`（不是 `MainActivity`）；release 包名 `dev.aaa1115910.bv`。
- 抓 App 日志：`adb logcat --pid=$(adb shell pidof dev.aaa1115910.bv.debug)`。

## 常见坑：B 站接口字段类型不稳

- `bili-api` 实体里有些字段 B 站在不同登录态返回类型不一致。例如动态接口 `module_author.following` 登录态也可能返回布尔 `true/false`（而非数字），实体若声明为 `Int` 会抛 `JsonDecodingException (Unexpected symbol 't' in numeric literal)`，导致整页订阅动态解析失败。
- 处理方式：用容错反序列化器（见 `bili-api/.../http/util/SafeIntSerializer.kt`，兼容 Int/Boolean/数字字符串/null），通过 `@Serializable(with = SafeIntSerializer::class)` 标到易变的 Int 字段上。全局 Json 已开 `coerceInputValues`+`ignoreUnknownKeys`，但 coerce 只兜底 null，布尔仍会崩，不要以为开了 coerce 就够。

## 代码结构约定

- TV 端页面放在 `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/`
- TV 端组件放在 `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/component/`
- 共享字符串资源放在 `app/shared/src/main/res/values/strings.xml`
- B 站 API 调用封装在 `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/BiliHttpApi.kt`
- 数据仓库放在 `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/`

## 远程操作参考

- 用户偏好 arm64-only APK，但本项目 `app/build.gradle.kts` 当前配置为 universal APK（`splits` 被注释）。如需修改，需与项目现有发布习惯保持一致。
- 默认 OpenClaw 实例路径：`~/.openclaw/`，与 BV 项目无关，不要在此项目目录下修改。
