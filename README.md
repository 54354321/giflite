# 轻转 GIF

轻量、离线的 Android 视频转 GIF 工具：选择视频、截取一小段、设置体积上限并导出。

## 功能

- 双端时间轴与秒数精调，联动视频预览。
- 文件大小上限、帧率、颜色数量设置。
- 自动等比缩放、自定义尺寸、裁剪/留边/拉伸。
- 通知栏后台转换、保存到相册、系统分享。
- 系统深浅色与可选动态配色。

界面基于 Android 原生控件及自定义绘制，参考 Material 3 Expressive 风格，未使用官方 Material 组件库。

## 构建

版本 0.5.4；Android 10（API 29）及以上。构建配置使用 JDK 17、Gradle 8.9、Android Gradle Plugin 8.7.3、Android SDK 35。

环境版本依据：[Android Gradle Plugin 8.7 兼容性说明](https://developer.android.com/build/releases/agp-8-7-0-release-notes)。

此源码包不包含 Gradle Wrapper。先安装 Gradle 8.9、JDK 17 和 Android SDK，通过 `ANDROID_HOME` 配置 SDK（或在本地创建不提交的 `local.properties`）。在项目根目录执行：

```sh
gradle :app:assembleDebug
```

调试包输出：`app/build/outputs/apk/debug/app-debug.apk`。首次构建需要下载构建依赖，应用运行不需要网络权限。

也可用已安装的 Gradle 生成 Wrapper 并将生成文件提交到仓库：

```sh
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

Release 签名需自行配置；仓库不包含发布密钥或签名配置。自行生成的调试签名与既有安装包不一致，覆盖安装前需先卸载旧版本。

## 目录

```text
.
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/izumikonata/giflite/   # 界面、预览、GIF 编码、后台任务
│       └── res/              # 主题、图标、动画
├── tests/                    # 独立 Java 与 Python 检查
├── docs/
│   ├── AI-CONTEXT.md     # 给 AI 助手的项目交接说明
│   └── TESTING.md
├── .gitattributes
├── .gitignore
├── LICENSE
├── build.gradle
├── gradle.properties
├── settings.gradle
└── README.md
```

测试方法和验证边界见 [docs/TESTING.md](docs/TESTING.md)。AI 助手接手本项目请先读 [docs/AI-CONTEXT.md](docs/AI-CONTEXT.md)。

## 许可证

本项目使用 [MIT 许可证](LICENSE)。
