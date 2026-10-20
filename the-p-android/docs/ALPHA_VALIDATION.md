# Alpha 验证记录（2026-10-20）

本记录对应当前工作区的 The P `5.0.0-alpha01` 原生工程；不是旧 FitWave 的 Web 测试结果。

已执行并通过：

- `spotlessCheck`：Kotlin 与 Gradle Kotlin DSL 格式检查。
- `assembleDebug`：生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `:core:model:test`：7 项业务规则测试，0 失败、0 跳过。
- `:core:data:testDebugUnitTest`：8 项导入/备份测试，0 失败、0 跳过。
- `lintDebug --no-parallel`：Android 静态检查。
- 云环境安装脚本重跑成功；SDK/JDK/Gradle 均已在当前实例实际安装。

工具链：完整 JDK 21、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Room 2.8.4。APK 最低 API 26，目标 API 36，显示名称 The P，调试包名 `com.thep.android.dev`。Logo 与批准原图 SHA-256 完全一致。APK 没有声明网络、麦克风或悬浮窗权限。

仪器测试使用 Android 15 / API 35 专用软件模拟器；最终结果在本文件末尾登记。云环境没有 `/dev/kvm`，不使用这次运行作真实性能、耗电或发热结论。运行前需要亮屏和解锁。

本轮修复：

- V5 JSON 导出必须显式保留格式/版本标识，防止自己的备份被误判成旧格式；已加入回归测试。
- 旧版补录的训练日期与打卡时间分别保留，跨午夜及补录记录按训练日期统计。
- 串行执行 Lint，避免与并行编译同时进行时触发 Kotlin 分析器内部错误；没有禁用检查或创建忽略基线。
- 模拟器的 System UI 无响应和息屏曾干扰界面测试，已诊断和恢复；未将受阻运行计为通过。

尚需小米 15 Pro 真机执行：独立安装、强杀进程后续练、键盘/系统字体布局、实际旧 JSON 的导入对账、长时间使用。未提供真实训练 JSON，本轮只使用合成数据。没有接入 DeepSeek、云同步、语音、悬浮窗、小组件或真实 Live2D。

Room 当前为首次 Alpha 的 schema v1，没有已发布的旧 Room 版本可升级；关闭重开持久性及事务回滚通过设备测试验证，不能把它们称为 v1→v2 迁移测试。

原生工程及 CI 以新增文件交付，旧 FitWave 的工程、锁文件、素材和训练数据未修改。代码提交及远端同步状态以 Git 仓库记录为准；环境配置草稿已保存，尚不代表已发布或已验证新任务恢复。Windows 同步及构建步骤见工程 README；同步源码后仍需本地构建才会生成新的 APK。

## 最终仪器测试结果

最终完整命令：

```shell
bash scripts/cloud-gradle.sh -Dddmlib.getprop.timeout.sec=120 :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-parallel
```

- Room：2 项通过，0 失败、0 错误、0 跳过。
- 应用：6 项通过，0 失败、0 错误、0 跳过。
- 合计 JVM 15 项 + 仪器测试 8 项 = **23 项通过**。

应用测试实际执行了生产 MainActivity 启动、The P 品牌显示、设置与进度导航；以及训练快照、上次重量、重复结束、独立计划选择、V5 备份往返、V4 幂等导入、无效输入拒绝和原生逐组录入。界面录入验证了 20.25 kg / 10 次的完成保存和 Room 中的 20250 克。

当前实例最终完整仪器测试成功；这不代表小米 15 Pro 真机验收、发布环境或新任务恢复已完成。详细 XML 和 HTML 位于各模块 build 目录，可用上述命令重新生成。

## Windows 构建辅助脚本

新增 `scripts/build-windows.ps1`，解决 PowerShell 默认 Java 8 无法启动新工程的问题。使用官方校验和验证的 PowerShell 7.4.6，在云端验证了脚本语法、显式选择 JDK 21、拒绝 Java 8、旧 JAVA_HOME 指向 Java 8 时自动发现 JDK 21，以及退出后恢复环境变量。Windows 路径选择使用临时目录模拟；未在用户 Windows 电脑执行，不代表已完成本机 Windows 构建。脚本不安装软件、不永久修改环境变量。
