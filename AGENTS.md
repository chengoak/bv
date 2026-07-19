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

## 代码结构约定

- TV 端页面放在 `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/`
- TV 端组件放在 `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/component/`
- 共享字符串资源放在 `app/shared/src/main/res/values/strings.xml`
- B 站 API 调用封装在 `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/BiliHttpApi.kt`
- 数据仓库放在 `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/`

## 远程操作参考

- 用户偏好 arm64-only APK，但本项目 `app/build.gradle.kts` 当前配置为 universal APK（`splits` 被注释）。如需修改，需与项目现有发布习惯保持一致。
- 默认 OpenClaw 实例路径：`~/.openclaw/`，与 BV 项目无关，不要在此项目目录下修改。
