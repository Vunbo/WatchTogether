# WatchTogether

WatchTogether 是一款基于 TVBox 订阅生态的 Android 影视聚合、直播与一起看应用。项目使用 Kotlin 与 Jetpack Compose 开发，支持影视订阅、聚合搜索、点播播放、直播频道、一起看房间、应用更新检测与外部一键导入。

> 本项目不内置、不提供、不存储任何影视资源。所有影视与直播内容均来自用户自行配置的数据源。

## Features

- 影视订阅管理：支持单仓、多仓配置，支持订阅验证、切换、删除与缓存。
- 聚合搜索：支持多站点搜索、搜索源筛选、历史搜索与建议词。
- 点播播放：支持线路、选集、收藏、历史、续播、片头片尾、全屏手势与画面缩放。
- 直播模块：支持直播源解析、分组频道、线路切换、EPG、收藏与回看入口。
- 一起看：支持创建/加入房间、聊天、房主同步、进度同步、倍速同步与选集同步。
- 应用更新：支持启动静默检测更新、设置页手动检测更新、GitHub Release 下载更新包。
- 外部导入：支持通过自定义 URL Scheme 从外部一键导入影视或直播订阅。

## Tech Stack

- Kotlin
- Jetpack Compose / Material3
- AndroidX Media3 ExoPlayer
- OkHttp / Gson / Coroutines
- Room / DataStore
- GitHub Actions

## Requirements

- Android Studio
- JDK 17
- Android SDK
- Android 7.0+，`minSdk 24`

当前版本仅集成 ExoPlayer 播放内核。

## Build

Clone the repository:

```bash
git clone https://github.com/Vunbo/WatchTogether.git
cd WatchTogether
```

Build debug APK on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Build debug APK on Linux/macOS:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Subscription

应用内可在设置页添加两类订阅：

- 影视订阅管理：用于点播、搜索、详情与播放。
- 直播订阅管理：用于直播分组、频道与线路播放。

多仓订阅示例：

```json
{
  "urls": [
    {
      "url": "https://example.com/source-a.json",
      "name": "Source A"
    },
    {
      "url": "https://example.com/source-b.json",
      "name": "Source B"
    }
  ]
}
```

单仓订阅可直接填写对应订阅地址。直播订阅可填写常见 txt/m3u 直播源地址，具体可用性取决于订阅源、网络环境与源站状态。

## URL Scheme Import

WatchTogether 支持通过自定义 URL Scheme 导入订阅：

```text
watchtogether://import?type=vod&name=Demo&url=https%3A%2F%2Fexample.com%2Fapi.json
```

参数说明：

- `type`：订阅类型，`vod` 表示影视订阅，`live` 表示直播订阅。
- `name`：配置名称。
- `url`：订阅地址，建议进行 URL 编码。

直播订阅示例：

```text
watchtogether://import?type=live&name=Live&url=https%3A%2F%2Fexample.com%2Flive.txt
```

## Watch Together

一起看功能需要配套 WebSocket 服务。创建房间时可填写服务地址，加入房间后会同步房主当前播放的视频、线路、选集、播放进度和倍速状态。

基础能力：

- 创建房间与加入房间
- 房间聊天
- 房主控制播放状态
- 成员同步房主播放进度
- 断线重连与主动同步

服务端可独立部署，客户端只需要配置可访问的服务地址。

## CI/CD

项目已配置 GitHub Actions：

- `.github/workflows/android-ci.yml`：提交或 Pull Request 时构建 debug APK。
- `.github/workflows/android-release.yml`：推送版本 tag 时构建 release APK，生成 `update.json`，并发布到 GitHub Release。

版本号统一维护在：

```text
app/version.properties
```

发布版本时，tag 需要与版本名一致，例如：

```bash
git tag -a v1.1.1 -m "Release v1.1.1"
git push origin v1.1.1
```

更完整的发布流程见：

```text
docs/release.md
```

## Project Structure

```text
app/src/main/java/com/vunbo/watchtogether/
├── config/        # 应用信息与更新配置
├── data/          # 数据源、仓库、本地数据、直播、更新等
├── navigation/    # 页面导航
├── ui/            # Compose 页面与组件
└── di/            # 依赖入口
```

关键模块：

- `ui/home`：首页与榜单
- `ui/search`：搜索
- `ui/detail`：播放详情
- `ui/player`：点播播放器
- `ui/live`：直播
- `ui/watchtogether`：一起看
- `ui/settings`：设置
- `data/subscription`：订阅管理
- `data/update`：应用更新

## Disclaimer

本项目仅供学习交流使用。

WatchTogether 不提供、不存储、不分发任何影视内容。应用中的影视、直播、搜索与播放结果均来自用户自行配置的数据源。请在遵守当地法律法规和相关服务条款的前提下使用本项目。
