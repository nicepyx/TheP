# 如何把 The P 交给 Codex

## 本地 Codex（推荐）
1. 在 Windows 创建 `D:\githubProject\TheP_Android`。
2. 将本压缩包内的 `AGENTS.md`、`docs/`、`assets/` 拷贝到新项目目录根部。
3. 在 Codex 桌面应用/IDE 扩展/CLI 中打开**该目录**；确认 Codex 能查看这些文件。
4. 发送如下首条消息（见下一段）。
5. 先让 Codex只读概述方案，然后再分阶段批准实施；第一阶段只做 Alpha，不允许修改旧 V4.x 项目。

## 如果使用网页 Codex Cloud
将此包内容提交到一个 Git 仓库中，连接该仓库/云环境；或在支持上传文件的界面直接附加这些文件。网页云端环境**不能直接读取**你电脑上的 `D:\...` 文件。确认 Codex 的云端目录里实际存在这些文件后再开始。

## 首条消息（复制给 Codex）
> 我们正在将旧 FitWave 重构为新的 Android 原生 App **The P V5.0**。请先阅读仓库根目录的 AGENTS.md、docs/PROJECT_CONTEXT.md、docs/V5_ARCHITECTURE.md，以及 assets 的 Logo 和 A1 吉祥物参考图。它们是此前产品决策的交接材料，而不是已完成代码。请先只读分析、总结你理解的产品目标、明确第一阶段 V5.0 Alpha 的开发范围和实际验收标准，确认不覆盖任何旧 FitWave 工程或旧用户数据，然后给出你准备创建的 Kotlin + Compose + Room 工程结构及第一批实施步骤，等待我批准后再编码。请用中文沟通。

## 其他说明
- 此交接包包含**关键信息摘要，不是原对话逐字记录**；不会自动把 ChatGPT 对话历史合并进 Codex 历史。
- 最终显示名称为 `The P`；`V5_ARCHITECTURE.md` 的旧标题 `FitWave V5.0` 属于品牌更名前的设计文档，凡与 `AGENTS.md` 冲突，以 `AGENTS.md` 已确定的 The P 品牌决定为准。
- 不包含用户真实的 V4.x 训练 JSON，也不包含签名证书/API Key；应在后续用户确认后安全提供。
