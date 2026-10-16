# AI-CONTEXT.md — 给 AI 的项目交接说明

本文件面向后续接手本项目的 AI 助手（而非人类用户）。目的是让你在不重新翻遍全仓库的情况下，快速建立准确的工程认知，避免重复推导和重复踩坑。

阅读顺序建议：本文件 → `README.md` → `docs/TESTING.md` → `app/src/main/java/cn/giflite/MainActivity.java`。

---

## 1. 项目一句话定位

`cn.giflite` / 轻转 GIF：一个**完全离线**的 Android 视频转 GIF 工具。单 Activity，原生 Java + 原生 View 体系，**零第三方运行时依赖**（没有 Compose、没有 Material Components 运行库、没有 Kotlin）。

工程约束（务必遵守，改动前先确认）：
- `minSdk 29`（Android 10），`targetSdk 35`，`compileSdk 35`，JDK 17。
- 源码兼容性设为 **Java 8**（`sourceCompatibility JavaVersion.VERSION_1_8`）——不要用 Java 9+ 的语法或 API。
- **不得引入网络能力**。Manifest 里没有 `INTERNET` 权限，这是产品的核心卖点，不是遗漏。
- 不得引入全盘文件权限。当前权限仅 5 项：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`FOREGROUND_SERVICE_MEDIA_PROCESSING`、`POST_NOTIFICATIONS`、`WAKE_LOCK`。
- 界面为原生控件 + 自定义绘制，风格上**参考** Material 3 Expressive。文档措辞须谨慎：写「参考」而不是「符合 M3E 规范」——因为并未引入官方组件库，行为细节不保证与官方一致。

---

## 2. 架构总览

分层思路是**「纯逻辑策略类 + 薄 UI」**。这是本项目最重要的设计约定：所有可测试的业务规则都被抽成无 Android 依赖的纯 Java 类，UI 层只负责调用。

### 纯策略层（可直接用 JDK 编译，不需要 Android SDK）

| 类 | 职责 |
|---|---|
| `ExportPolicy` | 输出尺寸/比例计算：`ratio()` 解析比例预设、`fitRatio()` 等比适配长边、`dimensions()` 计算目标尺寸、`destination()` 计算裁剪/留边/拉伸的绘制矩形、`validateDimensions()` 校验尺寸合法性与总像素上限 |
| `SelectionPolicy` | 时间片段选择逻辑：端点顺序、越界钳制、极短片段、毫秒精度终点、触感节流 |
| `TimelineTouchPolicy` | 时间轴双抓手的触点归属：两端热区重叠时按首次水平拖动方向决定操作哪一端 |
| `ColorPolicy` | 配色策略：读取动态色、对比度校验（不合格则回退绿色）、深浅色选择 |
| `UiPolicy` | UI 状态机：`restoreSession()` 判断冷启动/主题重建/任务恢复、`changesRatio()` 判断是否需要显示裁剪选项 |
| `GifEncoder` | GIF 编码器本体：调色板量化、块编码、循环标志。内部类 `Blocks`/`Box`/`Palette` |

### Android 层

| 类 | 职责 |
|---|---|
| `MainActivity` | 唯一 Activity。表单状态保存、预览挂载、时间轴联动、主题切换 |
| `ExportService` | 前台服务，承载实际转换。Android 15+ 用 `mediaProcessing` 类型，旧版用 `dataSync` |
| `ResultProvider` | `ContentProvider`，`authorities="cn.giflite.result"`，用于把结果文件以 content URI 提供给分享/保存，`grantUriPermissions=true` |
| `RoundedVideoView` | TextureView 预览，做 8dp 圆角裁切（仅 UI 效果，不影响导出） |
| `RangeTimeline` | 自绘双端时间轴控件 |
| `ExpressiveControls` / `ExpressiveSlider` / `AnimatedSpinner` / `SelectBackground` / `AppPalette` | 自定义绘制控件与色板 |
| `TimelineTouchPolicy` / `SelectionPolicy` / `UiPolicy` | 见上，供 UI 调用 |

### 关键状态约定
- `MainActivity` 用 `PROCESS_TOKEN`（进程内随机 UUID）判断 `onSaveInstanceState` 的状态是否属于同一进程。**冷启动必须显示全新表单**，不恢复上次视频和结果；只有同进程的主题切换才恢复界面。
- `ExportService` 持有转换所有权，Activity 销毁不取消任务。任务结束（完成/取消/失败/系统超时）必须停服务并释放唤醒锁。
- 转换可能因文件超限而**多轮降尺寸**，通知里的进度是**本轮进度**，不是总进度。不要"优化"成总进度，会误导用户。

---

## 3. 测试体系（非标准，务必先读这节）

**本项目没有使用 Gradle 的 instrumentation test，也没有 JUnit。** 测试是独立的 `main` 程序，靠 `AssertionError` 失败、靠 `System.out.println` 报数。这样设计的目的是让测试能在无 Android SDK 的纯 JDK 环境下跑，便于快速回归。

- Java 测试：`tests/*.java`，编译目标见 `docs/TESTING.md`。运行前需创建 `work/` 目录（`EncoderTest`、`SizeTest` 会把 GIF 写到那里，已在 `.gitignore` 排除）。
- Python 检查：`tests/check_theme.py`（解析 `res/` 校验深浅色对比度 ≥ 4.5:1）、`tests/verify_gifs.py`（用 Pillow 独立解码 GIF，需先跑 `EncoderTest`）。
- `tests/preview_regression.py`：在 Windows 上用**严格假播放器**编译并测试真实的 `RoundedVideoView` 源码。假播放器会**禁止在 Prepared 状态调用 pause**，用来捕获非法状态转换。

**验证边界（重要，不要越界声称）**：
- 这些测试验证的是**逻辑与状态机**，不是真实视频解码、渲染、触感或设备回调时序。
- 本仓库整理时只做了「Java 编译 + 资源/DEX 打包」级别验证，**未运行完整 Gradle 构建**。
- 任何涉及真机视觉/触摸/动画的结论都必须标注为「待真机验证」，不得把编译通过当作视觉验收。这是本项目的文档纪律，请继续保持。

### 本地跑一遍（已验证可用）
```sh
JDK=<JDK17目录>
mkdir -p verify/classes work
"$JDK/bin/javac" -encoding UTF-8 -d verify/classes \
  app/src/main/java/cn/giflite/{ColorPolicy,ExportPolicy,GifEncoder,SelectionPolicy,TimelineTouchPolicy,UiPolicy}.java \
  tests/{ColorPolicyTest,GeometryTest,SelectionPolicyTest,TimelineTouchTest,UiPolicyTest,EncoderTest,SizeTest}.java
for T in GeometryTest UiPolicyTest ColorPolicyTest TimelineTouchTest SelectionPolicyTest; do
  "$JDK/bin/java" -cp verify/classes "$T"
done
```
期望结果：Geometry 21 项、UiPolicy 12 项、ColorPolicy 10 项、TimelineTouch 9 项、SelectionPolicy 1020 项断言通过。

---

## 4. 已完成的脱敏处理（不要回退）

本仓库是从一个包含真实使用上下文的内部版本整理而来。以下内容已被移除或泛化，**属于有意为之，请勿"恢复"**：

- 移除了全部与特定 Android 应用分发平台相关的表述和字段（包括作为体积上限默认值来源的说明）。
- 移除了所有真实设备型号与厂商系统标识，包括 README 里针对特定机型的更新说明。
- 测试中的设备指纹分辨率已泛化为通用值：原 `1264×2780` → `1080×2400`，`2354` → `2400`；`GeometryTest` 的断言期望值相应改为 `576×1280`。`EncoderTest` 原本就使用 1080×2400，未改动。
- 删除了内部开发过程的构建脚本、版本更新日志、截图反馈记录和本机绝对路径。
- 仓库不包含任何签名密钥、口令或凭据。`.gitignore` 已覆盖 `*.jks`/`*.keystore`/`local.properties` 等常见泄漏点。

提交前建议自检：
```sh
grep -rniE "coolapk|realme|coloros|neo8|1264|2780|2354|C:\\\\Users" .
```

---

## 5. 已知限制与待办

**功能边界（当前版本有意不做）**：不含实况图、批量处理、HDR 保真、逐帧剪辑。GIF 无音轨，最多 18,000 帧。

**技术限制**：
- 视频兼容性完全依赖系统解码器，不内置解码器。
- 预览定位与停止精度受视频帧间隔、解码器与系统调度影响，**不承诺逐帧精度**。
- 时间按 GIF 的 10ms 单位舍入。
- 厂商若仅公开默认色板，应用无法识别用户另外选择的厂商主题色。
- 桌面图标最终由启动器渲染，不能保证自动跟随主题反色。

**明确的待办**：真机验证尚未完成，覆盖项包括——界面渲染与动画、触摸交互、后台策略（省电限制/系统清理）、辅助功能（TalkBack）、视频解码与旋转、三端比例处理的设备表现、ColorOS 桌面图标及缓存更新。

---

## 6. 改动本项目时的注意事项

1. **策略逻辑放纯类，不要放 Activity。** 新增业务规则时优先扩展现有 `*Policy` 类，并补上对应的独立测试断言。
2. **不要为了让代码"更现代"而引入依赖。** 零依赖是刻意的工程选择（安装包极小、无供应链风险、可离线构建）。
3. **改状态机前先看 `preview_regression.py`。** 播放器状态约束（尤其 Prepared 状态不得 pause）是通过测试固化的，破坏它会导致首次拖动/播放把播放器推进 Error。
4. **多轮降尺寸的进度语义**是有意设计，改动前确认产品意图。
5. **文档措辞纪律**：未验证的事写「待验证」；参考而非符合；不宣称官方组件一致性。
6. 改动后至少跑一遍第 3 节的 Java 测试，并同步更新 `docs/TESTING.md` 的验证边界说明。
