# 测试

Java 测试是独立 main 程序，不属于 Android Gradle 的 instrumentation tests。

在项目根目录使用 JDK 17 编译下列纯 Java 类与测试到本地 `work/testclasses`：

- 源码：ColorPolicy、ExportPolicy、GifEncoder、SelectionPolicy、TimelineTouchPolicy、UiPolicy。
- 测试：ColorPolicyTest、GeometryTest、SelectionPolicyTest、TimelineTouchTest、UiPolicyTest、EncoderTest、SizeTest。

对应文件分别位于 `app/src/main/java/com/izumikonata/giflite` 与 `tests`。运行 EncoderTest 和 SizeTest 前创建 `work` 目录；两者的测试 GIF 写入该目录，已在 .gitignore 中排除。

Python 检查：

```sh
python tests/check_theme.py
python tests/verify_gifs.py
```

verify_gifs.py 需要先运行 EncoderTest，并安装 Pillow。

Windows 上的播放器模拟测试：

```text
python tests/preview_regression.py <JDK目录> work/preview-tests
```

该脚本编译实际预览类并使用严格的假 Android 播放器检查状态调用；它不验证真实视频解码、渲染或设备回调。

## 当前验证边界

本仓库整理时使用本地 Android 工具完成资源、Java、DEX 打包验证；未运行完整 Gradle 构建。真机界面、动画、后台策略、辅助功能和视频解码仍需设备测试。
