# The P · V5.0 Alpha

独立 Android 原生工程。Kotlin / Jetpack Compose Material 3 / Room / Hilt / Coroutines / StateFlow / DataStore。界面为中文，应用显示名称为 **The P**，调试包名为 **com.thep.android.dev**，可与旧 FitWave 并存。

旧版根目录 `android/`、HTML、Capacitor、训练数据均不属于此工程，不运行旧版 `android:init` 或 `android:sync`。原始品牌素材保留在仓库 `assets/`；新工程的 Logo 使用原图，A1 使用现有表情素材，尚无 Live2D。

## 构建

需要完整 **JDK 21**、Android SDK Platform 36、Build Tools 35.0.0（AGP 默认）和 36.0.0、Platform Tools。最低 Android 8.0 / API 26，目标 API 36。

在 Android Studio 中直接打开本目录，让 IDE 配置本机 SDK 路径。

```shell
./gradlew spotlessCheck assembleDebug testDebugUnitTest :core:model:test
./gradlew lintDebug --no-parallel
```

Windows 使用 `gradlew.bat`。APK：`app/build/outputs/apk/debug/app-debug.apk`。

### Windows 同步与构建

在 PowerShell 中更新本机仓库，再构建新原生工程：

```powershell
Set-Location D:\githubProject\TheP_Android
git pull --ff-only origin main
# 上一步成功后继续；若提示冲突或无法快进，请先保留本地改动并处理分支差异。
Set-Location .\the-p-android
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-windows.ps1
```

该脚本自动查找 Android Studio 自带或已安装的完整 JDK 21，临时为本次构建设置 Java 环境，退出后恢复。不会修改系统环境变量或旧工程。`-ExecutionPolicy Bypass` 仅作用于本次子进程，不修改系统策略。若 JDK 安装在自定义位置，可追加 `-JdkPath 'D:\Tools\jdk-21'`；未找到 JDK 21 时会明确停止，不再使用 Java 8 继续构建。只检查 Java 可追加 `-CheckOnly`。脚本不安装 JDK 或 Android SDK，SDK 仍由 Android Studio 配置。

新 APK 的完整路径为 `D:\githubProject\TheP_Android\the-p-android\app\build\outputs\apk\debug\app-debug.apk`。
Git 同步源码不会自动重新生成 APK；旧目录 `android\app\build\outputs\apk\debug` 的时间不代表 V5 的构建时间。

云环境：

```shell
bash scripts/setup-cloud.sh
bash scripts/cloud-gradle.sh spotlessCheck assembleDebug testDebugUnitTest :core:model:test
bash scripts/cloud-gradle.sh lintDebug --no-parallel
```

安装脚本在 `/workspace` 下保存 SDK/JDK/Gradle 缓存，保持 TLS、官方校验和验证；不写入旧工程。每个云任务已经隔离，使用现有 checkout，不创建额外 Git worktree。

## 仪器测试

连接专用 Android Emulator 或测试设备后：

```shell
./gradlew :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Room 测试使用独立临时数据库，界面测试使用内存数据库。不要将测试 APK 安装到存有真实数据的日常使用环境。小米 15 Pro 的验收步骤见 [Alpha 验收](docs/ALPHA_ACCEPTANCE.md)。CI 配置提供构建、单元测试、Lint 与格式检查；CI 本身是否执行，以 GitHub Actions 结果为准。

## Alpha 功能

- 首页、训练、进度、设置四个原生页面。
- 默认四分化；计划复制/切换，训练日名称/星期编辑，动作增删/排序/组数/次数/休息编辑。
- 正式组、热身组、重量/次数/RIR；保存、完成、撤销、上次重量参考、备注、暂停/继续/结束/放弃。
- 已确认训练组事务落盘，历史使用快照；Room 是唯一训练数据来源。
- 截止时间驱动的休息计时，前台使用单调时钟。进程重启后按保存的墙钟截止时间恢复，剩余时间限制在一小时内；人为改时仍可能影响重启后的恢复。后台到时通知留给 Beta。
- 历史、正式组训练量、每周次数、PR、估算 1RM（仅 1–12 次有效负重组）、体重/腰围及基础有氧。
- SAF 文件选择器导入/导出。JSON 文件限 10 MB；SHA-256、确定性旧 ID、事务、重复识别、冲突跳过、导入日志与原始 V4 JSON 保留。

## 数据约定

重量以整数克存储，腰围以整数毫米存储。完成组与场次更新时间在同一事务内提交。已完成组的修改留存变更前内容。计划编辑不改变已经开始的场次快照。训练日期独立保存：新训练按开始日，旧版按 JSON 键中的日期；补录时间另行保留，跨午夜不会将训练算入错误的一周。默认不自动备份到 Android 云端，需用户主动导出 JSON。

V4 迁移读取 `forge_training_v33_2026` 导出的根 JSON（`sessions/cardio/body/settings`），不复制 V4.2 SQLite。导入确认只写入 V5；未知字段和旧偏好保存在本机原始导入记录中。未结束旧场次标记为已放弃并保留组数据，不自动接管当前训练。旧自定义课表作为独立计划保留。有氧按原始 `walk` 分钟和 `stairs` 层数映射，不补造运动量。

V5 备份恢复：若当前只有未经修改的演示数据，则恢复备份课表；已有用户内容时保留现有课表，追加未冲突记录，预览明确提示。不会静默覆盖已有场次。JSON 导出包含数据库业务内容和迁移日志；DataStore 的自动休息偏好单独保留，不包含在训练备份中。

数据库目前为首次 Alpha 的 schema v1，schema 文件纳入 Git。禁止破坏性迁移；未来变更需增加版本并实现实际迁移测试，当前没有虚构的 v1→v2 测试结果。

## 后续阶段

Beta：后台休息提醒、系统悬浮桌宠、Glance 与电量/权限回归。

RC：合法分层 A1、真实 Live2D 模型与运行时、透明渲染兼容性测试和降级。

正式版：语音、饮食细化、可选云同步与 AI。用户已确定 AI 使用 **DeepSeek**；通过安全后端代理，API Key 仅在服务端配置。当前无 API Key 输入框、无 AI 网络调用，不需要提供密钥。
