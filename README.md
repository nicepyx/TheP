# FitWave V4.2 · 奶霜丝带 · 智能训练伴侣

在用户已成功安装并完成 6/6 项真机测试的 **V4.1 重试修复版**基础上开发。继续使用 `com.fitwave.personal` 包名、原四分化计划和 `forge_training_v33_2026` 训练记录键。

## 功能状态（请在安装前阅读）

| 模块 | V4.2 提供的具体内容 | 验证边界 |
|---|---|---|
| A1 角色互动 | 保留粉白主题、多表情状态；增加训练/思考/鼓励动画状态总线和轻量 2.5D 动效 | **不是完成的 Live2D 模型**：缺少原画分层、Cubism `.moc3`、纹理与动画文件；`public/live2d/README.md` 为接入说明 |
| 原生语音 | Android SpeechRecognizer **点击说话**；TextToSpeech 朗读；训练日程/进度/体重/导航等无需网络的命令 | 识别质量及是否联网由手机的 Android 语音服务决定；未实现常开监听 |
| AI 对话 | 可选 Supabase Edge Function AI 代理；从自己的服务端调用兼容的 Chat Completions API；文本回复可 TTS | 需要你自己的 Supabase 项目与 AI API Key，在服务器端配置；默认未启用 |
| 云端同步 | Supabase 邮箱账号登录、单用户 RLS、明确上传/下载、冲突确认、可选登录期间自动备份 | 需要自行创建 Supabase 项目并运行 SQL；**首次不会自动同步**；备份非端到端加密 |
| SQLite | 原生 `SQLiteOpenHelper`；`state/snapshots/workout_sessions/workout_sets/body_metrics` 五张表；主数据镜像、20个恢复点、历史统计 | 原本的 Web 数据作为兼容输入，后续版本可以逐步把 SQLite 变为唯一数据源 |
| Android 桌面小组件 | 原生 `AppWidgetProvider`，展示当天安排、本周已完成训练；点击打开 App | 在桌面上手动添加，App 打开或训练数据保存时更新；不是常驻每秒刷新 |
| V4.1 桌宠 | 原生悬浮窗、吸边拖拽、后台休息倒计时、权限处理 | 保留；V4.2 安装脚本自动修正之前的生命周期 `public` 可见性问题 |

**状态：提供可供 Android Studio 构建的源代码；本环境无 Android SDK，尚未完成 Gradle 真实编译与小米 15 Pro 的 V4.2 真机验收。**

## Windows 首次构建（独立解压目录）

在 V4.1 的 App 内先进入设置导出完整 JSON 备份，确认文件确实保存到了手机外部或电脑上。不要先卸载已安装的 V4.1。

```powershell
cd D:\githubProject\FitWave_App_V4.2
npm.cmd install
npm.cmd run smoke
npm.cmd run check
npm.cmd run android:init
npm.cmd run android:open
```

Android Studio → **Build → Generate App Bundles or APKs → Generate APKs**。APK 一般位于 `android\app\build\outputs\apk\debug\app-debug.apk`。

工程已经生成 `android` 文件夹后，**不要重复执行 `android:init`**。以后更新源码用：

```powershell
npm.cmd run android:sync
npm.cmd run android:open
```

升级到同一台电脑的 debug 签名通常可以直接覆盖；如果提示签名不一致，**先确认 JSON 备份**，再决定是否卸载。用户原先改过的 V4.1 `onPause/onResume` 访问修饰符已经由 V4.2 安装脚本自动修正。

## 手机中如何使用新模块

启动新 App，点击右下角的粉色 `✦`（A1 助手与数据中心），里面包含：

1. **语音助手**：按下说话，首次授予麦克风权限；可以问「今天练什么」「我这周练了几次」「最近体重多少」「打开训练页面」。点击「播报回答」用系统 TTS 朗读。
2. **SQLite**：查看数据库状态，手动创建恢复点；恢复会进行二次确认，恢复前先留一个自动备份。每次训练结束也会留下恢复点，最多 20 条。
3. **云端备份**：先配置你自己的 Supabase 项目 URL 和公开 anon/publishable key，再登录邮箱账号。上传和下载有明确按钮。发现别的设备上的云端版本变化时，不会直接自动覆盖。
4. **小组件**：在小米桌面长按空白处，找到「添加小组件 / 小部件」→ 找到 `FitWave` → 放到桌面。打开 App 或保存训练时同步数据。
5. **角色动作预览**：可轮流触发 A1 角色的轻量化状态；不是正式 Live2D 骨骼动画。

## 开通自己的云同步

1. 在 [Supabase](https://supabase.com) 新建 Project。
2. 打开 SQL Editor，执行 [`backend/supabase/schema.sql`](backend/supabase/schema.sql)。该文件会启用 Row Level Security，账号只能访问自己的备份行。
3. 在项目配置中复制 **Project URL** 和 **anon/publishable key**；**不要使用 service_role 或 secret key**。
4. 在 Auth 中开启邮箱认证。注册后如果启用了邮箱验证，先点击验证邮件，再登录。
5. App 里填入 URL 和公钥，登录后手动执行一次上传。另一台设备登录同账号后，主动点击「从云端恢复」并确认覆盖。

可选 AI：将 [`backend/supabase/functions/fitwave-ai/index.ts`](backend/supabase/functions/fitwave-ai/index.ts) 部署为名为 `fitwave-ai` 的 Edge Function，并打开 JWT 验证。仅在 Supabase 服务端环境变量中配置 `AI_API_KEY`、`AI_MODEL`、`AI_BASE_URL`（可选），**不要把密钥写进 `index.html` / `src` / APK**。请设置服务端额度与频率限制，避免 API 费用失控。**只连接云账户不会自动使 AI 接口可用，Edge Function 还需另外部署。**

数据隐私：训练备份通过 HTTPS 上传到你自己的 Supabase 项目，云服务提供商仍可能处理或持有相应数据；当前不是端到端加密。如果不想使用远程服务，不填写配置即可，离线记录、SQLite、原生语音快捷指令、小组件仍可使用。

## 关于真正 Live2D 的实现边界

**目前从 A1 设计板裁切的 PNG/WEBP 并不是分层动画资产。**
真正的 Live2D 至少需要：可供绑定的 PSD 图层（前发、后发、眼睛、嘴、眉、面部、服装、躯体等），在 Live2D Cubism 中绑定参数、导出 `.moc3`、`.model3.json`、纹理及 motion，再按官方 SDK 许可安装 Android/Web 运行时。这部分不能由当前四张表情图片无损推导。

V4.2 已提供 `src/live2d.ts` 的统一角色状态接口和 `public/live2d/` 资源接入说明；**尚未提供真实 `.moc3` 模型，也未内嵌商业授权 SDK**。桌宠继续显示轻量表情动画；后续拿到合法分层模型后可以将其接到这一套状态机。

## 目录结构

```
src/v42.ts               A1 对话中心 + SQLite + 小组件 + Supabase 客户端
src/live2d.ts            角色表情/动作状态总线（正式 Live2D 接入层）
src/v42.css              主题控制台样式
native/android/com/fitwave/personal/next/
  FitWaveStorePlugin.java     SQLite 原生数据插件（归一化训练表）
  FitWaveVoicePlugin.java     SpeechRecognizer / TextToSpeech
  FitWaveWidgetProvider.java Android 桌面小组件
  FitWaveWidgetPlugin.java   小组件数据同步
scripts/install-v42-android.mjs
backend/supabase/schema.sql
backend/supabase/functions/fitwave-ai/index.ts
public/live2d/README.md
```

## 真机测试（V4.2 未完成）

- 上线前备份 V4.1 JSON、原地安装 V4.2，确认历史记录仍存在；SQLite 首次导入并且能建立、恢复恢复点。
- 用测试训练完成三组；后台切换其他 App、锁屏休息后核对组数和倒计时。
- 语音「今天练什么」、TTS、未授权麦克风时的错误提示，以及系统没有语音识别服务时的降级文案。
- 增加/移除桌面小组件，训练后检查进度是否更新；横屏和调整尺寸正常。
- 未配置云服务时，App 仍然离线运行；配置后测试邮箱注册、登录、手动上传/恢复、多设备冲突确认。
- 没有 Supabase AI Edge Function 时，开放式问题得到离线回退；部署后测试回答和 TTS，检查数据不会被 AI 直接修改。
- V4.1 悬浮服务仍可开启、拖动、吸边、隐藏、恢复和停止。

## 安全边界

- 不监听其他 App、不获取短信、联系人或无障碍信息；语音只在用户主动点击后识别。
- 云同步非强制，AI 密钥不内置 APK；不提供无限制服务器调用或没有认证的公众接口。
- Android 上的悬浮权限、麦克风和通知由用户主动授权；Android 可能限制后台功能。
- 小组件显示训练摘要，建议不要展示敏感健康数据；实际是否出现在锁屏取决于设备系统。
- 保留旧 JSON 导出作为离机备份方式；SQLite 数据同样会在用户清除 App 数据或卸载后被删除。
