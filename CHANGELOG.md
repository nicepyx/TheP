# FitWave V4.2 更新记录

- **A1 吉祥物**：新增统一的角色状态事件 `fitwave:assistant-emote`、专注/思考/鼓励轻量动态；完整 Live2D 分层素材与模型尚待制作。
- **语音**：Android 原生语音识别与语音合成，点击录音，无常驻录音服务；内置几个离线训练问答命令。
- **AI**：可选 Supabase JWT 认证的服务端 AI 代理示例，需用户自行部署并提供服务端密钥。
- **云同步**：可配置 Supabase 邮箱账户、手动上传/下载、覆盖前确认、可选自动上传；服务端 SQL 包含 RLS。
- **SQLite**：Android 原生数据库镜像，五张表，训练结束快照和恢复点，兼容 `forge_training_v33_2026`。
- **桌面小组件**：小米/Android 系统原生训练日程与周进度小组件，添加后点击可进入 App。
- **构建**：新增 `install-v42-android.mjs` 自动安装原生插件、布局、资源、权限及注册；修复 V4.1 MainActivity 生命周期方法的访问级别。
- **兼容性**：包名保持 `com.fitwave.personal`；V4.1 悬浮精灵、训练内容和原备份路径保留。

**开发状态：静态及重复安装脚本检查通过；无 Android SDK 的环境未进行 Gradle 编译和小米真机运行。**
