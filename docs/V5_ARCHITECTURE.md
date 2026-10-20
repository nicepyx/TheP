# FitWave V5.0 — Android 原生重构蓝图

**版本**：Architecture Blueprint 1.0（2026-10-20）  
**平台**：Android-only；首要测试设备：小米 15 Pro  
**角色主题**：A1 奶霜丝带（银白长发、粉瞳、奶霜粉视觉）；品牌与鲸鱼概念解绑  
**当前阶段**：架构设计，不代表 V5.0 代码或 APK 已完成。

## 1. 产品定位与范围

**核心产品**：离线可用的力量训练日志与训练计划管理软件。辅助功能：桌面动态角色、休息通知、身体指标、训练分析、桌面小组件、可选 AI 与云同步。

**三条主要用户路径**：
1. 训练：打开 App → 今日课表 → 查看上一练数据 → 逐组记录重量/次数/RIR → 倒计时 → 切出 App 仍可从悬浮卡查看状态 → 结束 → 训练历史与 PR 自动更新。
2. 角色：进入 App → A1 随状态变换表情与动作 → 用户显式开启桌面精灵 → 系统授权 → 桌面拖动与收起 → 训练/休息事件驱动表情 → 一键停止。
3. 长期数据：无网络也能记录 → Room 事务落盘 → 本地导出/导入 → 可选登录并后台同步 → 多设备冲突可解释、可恢复。

**重要边界**：不承诺系统强行停止后仍常驻；不在未经授权时读取其他 App 内容；不使用无障碍模拟点击；不把服务端 API key 写进 APK；尚无真实 A1 Live2D 分层模型。

## 2. 技术决策（ADR）

| ID | 决策 | 原因/约束 |
| --- | --- | --- |
| ADR-01 | Kotlin + Jetpack Compose / Material 3 | Android-only，统一原生 UI、状态及权限 |
| ADR-02 | MVVM + UDF；ViewModel 暴露 StateFlow | 降低页面与业务耦合，屏幕旋转等场景下状态可恢复 |
| ADR-03 | Room SQLite = 训练数据唯一权威来源 | 避免 V4.x LocalStorage/IndexedDB/SQLite 多份竞争 |
| ADR-04 | DataStore 存用户偏好，非训练关系数据 | 偏好与业务数据分离 |
| ADR-05 | Repository + 仅对复杂业务使用 UseCase | 保持边界，避免空泛过度抽象 |
| ADR-06 | Foreground Service + WindowManager | 可授权启动桌宠；按 Android 版本检查合法 FGS 类型和后台限制 |
| ADR-07 | 角色引擎接口化（L2D / Sprite fallback） | 目前无 .moc3；高低配、不同 OS 和权限可安全降级 |
| ADR-08 | WorkManager 处理可延迟云同步 | 不使用 WorkManager 做秒级休息倒计时 |
| ADR-09 | 所有训练写操作先写本地，再入同步 outbox | 离线优先且减少数据丢失 |
| ADR-10 | 重写 V5，旧 V4.x 仅作为**受控数据来源** | 不迁入原有 HTML 视图、Capacitor 插件和分散存储逻辑 |

**技术建议**：Kotlin、Compose、Material 3、Navigation Compose、Hilt、Coroutines/Flow、Room、DataStore、WorkManager、Glance、Kotlin Serialization、OkHttp/Retrofit（仅在网络功能阶段）。Gradle Kotlin DSL + Version Catalog；版本在创建工程时统一选择相互兼容的稳定版，不凭空锁死版本号。

## 3. 目标架构

```text
MainActivity / Compose Navigation
      │ 用户操作                    ▲ UI State (StateFlow)
      ▼                             │
Feature Screen & ViewModel ─────────┘
      │ Commands
      ▼
UseCases（跨功能业务，如 CompleteSet/FinishWorkout）
      │
      ▼
Repositories（唯一数据接口） ──────────────────┐
      │                                       │
      ▼                                       ▼
Room AppDatabase （唯一训练真相）        Outbox + SyncWorker → 安全后端
      │
      ├── Flow → Progress ViewModel → 进度曲线
      ├── Workout Events → PetStateCoordinator → Renderers
      ├── WidgetSnapshot → Glance Widget
      └── Session Snapshot → Android Overlay Service
```

**业务事件不是第二份数据库**：持久事件含事件 ID 供去重；实时角色事件不必长期存储。前台 Activity、Service、小组件从同一 Repository 读取快照，不各自维护互相冲突的训练主数据。

### 建议 Gradle 模块（循序建立）

```text
fitwave-v5/
├── app/                        # Application, MainActivity, Hilt, NavHost
├── core/
│   ├── model/                  # Workout, Exercise, Set, PetMode 等纯 Kotlin 模型
│   ├── database/               # Room Entity, DAO, Migration, AppDatabase
│   ├── data/                   # Repository, mapping, transaction, JSON importer
│   ├── designsystem/           # A1 Theme, Compose Components, typography
│   └── platform/               # 通知、权限、音频、clock 接口与适配
├── feature/
│   ├── home/                   # 今日训练/入口
│   ├── workout/                # 训练计划编辑、实时逐组记录、计时
│   ├── progress/               # 历史/PR/围度/图表
│   └── settings/               # 偏好/备份/权限/迁移入口
├── companion/                  # 后期：角色引擎、系统悬浮窗、状态协调
├── widget/                     # 后期：Glance 小组件
├── cloud/                      # 后期：Auth, SyncWorker, Outbox, API
├── assistant/                  # 后期：STT/TTS, AI 代理与技能边界
├── benchmark/                  # 后期：性能基准
└── build-logic/ + gradle/      # Convention plugins + Version Catalog
```

**模块依赖规则**：`app → feature → core:data → core:database`；feature 不得直接调用 Dao；角色模块不得直接写 workout_sets；cloud 只经 repository/outbox 处理数据；UI 不感知服务端 Key。单人开发第一阶段先建立 app + 4 core + home/workout/progress/settings，companion/widget/cloud 依次加入，不要一开始产生 20 多个空模块。

## 4. 核心数据模型（逻辑）

| 表 | 主要字段 | 规则 |
| --- | --- | --- |
| `exercise` | `id`, `name`, `primaryMuscles`, `equipment`, `instructions` | 内置动作固定稳定 ID，允许用户自定义 |
| `workout_plan` | `id`, `name`, `revision`, `active` | 同时允许多计划，仅一个当前计划 |
| `plan_day` | `id`, `planId`, `order`, `weekday` | 不将星期与历史记录强绑定 |
| `plan_exercise` | `id`, `dayId`, `exerciseId`, `order`, `targetSets`, `repMin`, `repMax`, `restSeconds` | 修改未来计划不得改变历史记录 |
| `workout_session` | `id (UUID)`, `planDayId?`, `startedAt`, `endedAt?`, `status`, `note` | 可中断、续练；开始后形成快照 |
| `session_exercise` | `id`, `sessionId`, `exerciseId`, `nameSnapshot`, `order` | 保证计划变更不改变历史 |
| `workout_set` | `id`, `sessionExerciseId`, `setOrder`, `weightGrams?`, `reps?`, `rir?`, `completedAt?`, `kind` | 逐组持久化；公斤以整数克保存，避免浮点误差 |
| `cardio_entry` | `id`, `date`, `kind`, `stairs`, `durationSeconds`, `note` | 爬楼/快走独立记录 |
| `body_measurement` | `id`, `recordedAt`, `weightGrams`, `waistMillimeters?` | 多次记录可以保留，不强制日期唯一 |
| `sync_outbox` | `eventId`, `entityType`, `entityId`, `op`, `updatedAt`, `status` | 幂等上传、失败重试 |
| `data_import_log` | `sourceHash`, `importVersion`, `importedAt`, `counts`, `status` | 避免重复导入和误覆盖 |

**DB 规则**：DAO 暴露 `Flow`；完整一组记录与对应 session 更新时间在一个事务内；对已经完成的组进行编辑可追溯；不得使用破坏性 migration 清空历史；导出 Room schema 并放进 git。

**训练统计**：PR 最大重量仅从已完成的有效负重组计算；估算 1RM 标记为估算，避免把数据不足的组当作真实极限；分析计算器是纯 Kotlin + 单元测试，不放到 ViewModel 中。

## 5. 训练状态与计时策略

**训练会话**：`NOT_STARTED → ACTIVE ↔ PAUSED → COMPLETED`，`ABANDONED` 需主动确认。

**角色状态**：`IDLE, GREETING, TRAINING, RESTING, READY, CELEBRATING, SLEEPING, TALKING`，由训练事件、用户交互和系统状态计算，支持优先级与防抖，避免一分钟内疯狂换动作。

**休息计时**：记录 deadline，UI 和浮窗按截止时刻重新计算剩余时间，而不是把 `setInterval` 的次数当真实时间。进程存活时可用单调时钟以抵抗手动改时；持久恢复需存墙钟截止时刻并处理重启、时区和设备时间变化。休息完成通知遵守 Android 系统限制，严格准时的系统唤醒需单独评估闹钟权限；WorkManager 不能作为秒级闹钟。

**桌宠 Service**：用户在前台显式开启并授权悬浮窗；一键隐藏、一键停止；通知栏显示可识别控制入口；按 Android 14/15+ 服务类型和后台启动约束设计；系统强制停止后不承诺自行恢复。透明浮窗默认小触控区域，碰到敏感界面要能隐藏，帧率降低/停帧优先省电。

**Glance 桌面组件**：今日训练、本周完成度、快速开始，按数据变更更新快照；不是 Live2D 动画容器。

## 6. A1 奶霜丝带动画落地

### 资产生产

1. 固定角色设定（面部、服装、配色、发型、身材比例、Q版和正常版），保留标准角色图。
2. 重新制作可分层的 PSD：前/后发、左右眼及眼皮、眼珠、眉毛、嘴、脸、脖子、衣服、手臂、身体、饰品，以及遮挡处补绘。
3. Cubism Editor 绑定表情、头部角度、眨眼、嘴型、呼吸、物理摆动、动作剪辑；导出模型及资源。
4. `AvatarRenderer` 接口：`load`, `play(state)`, `setSpeaking`, `pause`, `release`。优先在 Activity 做原生渲染 PoC，再测试透明悬浮环境；支持 Sprite fallback。
5. App 内角色与桌宠角色使用相同动作事件，但可使用不同大小的美术资产、低帧率和不同渲染方式。

### 关键兼容性/许可门槛

Live2D 官方最新平台支持列表对 **Cubism SDK for Java** 明示 Android 支持上限 API 34；用户的小米 15 Pro 如运行更高版本，不能未经测试假定 SDK for Java 兼容。技术预研应优先对比 `Cubism SDK for Native + OpenGL ES + JNI` 与 Java SDK，在 **实际手机系统版本** 上完成透明绘制、前后台切换、GPU 占用和内存压力测试，测试前保留 Sprite 降级。Cubism Core 是官方专有组件；不得从第三方无授权随意打包，正式发布前检查 Live2D 许可，尤其含 AI 互动的使用场景。

**动画分阶段**：M0 静态+状态切换；M1 眨眼/呼吸/头部运动；M2 发丝物理/嘴型跟读；M3 多动作融合、闲置省电、同步悬浮小精灵。

## 7. AI、语音、云端（独立于训练内核）

- **语音输入**：Android SpeechRecognizer，用户按住/点击启动；拒绝麦克风权限仍能打字；不持续录音。
- **语音播报**：TextToSpeech，检查语音引擎状态、中文音色及取消；说话驱动口型状态而非重复播放 GIF。
- **AI 代理**：App → 用户自己的 HTTPS 后端（会话鉴权/限流/预算）→ 模型服务。密钥仅存在服务端；本地离线规则可回答「今天练什么」而不依赖联网。
- **能力安全**：读训练历史需用户授权，AI 只返回建议；任何修改计划、训练组数、删除/覆盖数据的操作必须经用户明确确认后由业务用例执行。
- **云端**：Room 先提交，outbox 入队；WorkManager 按网络等约束上传；服务端要求行级鉴权（如 Supabase RLS）、UUID 幂等键、版本冲突处理及可恢复快照。默认关闭云同步，未经配置绝不自动上传。
- **数据隐私**：训练记录与照片属于私人信息；不要发送完整数据给 AI，优先抽取用户提问必要的统计；提供清除和导出入口；避免日志输出训练数据与访问令牌。

## 8. V4.x → V5 数据迁移（已核对当前 V4.2 工程）

**旧格式**：`index.html` 使用 `const VERSION=3, KEY='forge_training_v33_2026'`；根 JSON 包含 `sessions`, `cardio`, `body`, `settings`, `startDate` 等。`sessions` 的键为 `YYYY-MM-DD__mon/tue/thu/sat`；`session.sets[exerciseId]` 是组数组，组元素含 `weight`, `reps`, `done`；`completedAt` 标记场次结束。`cardio[date]` 包含 `stairs/walk/kind/done`；`body` 数组包含 `date/weight/waist`；`settings.overrides` 可更改训练设置。

**V4.2 原生 SQLite**：`fitwave_training_v42.db` 包含一份 `state.payload` JSON 镜像及 `workout_sessions`, `workout_sets`, `body_metrics`, `snapshots` 衍生表。**不要直接复制该数据库作为 V5 的 Room 库**，它不是 V5 的最终模型，且 V4.2 可能尚未在用户手机上实际完成数据迁移验证。

### 推荐迁移流程

1. **保护原始数据**：在正常工作的旧版 App 中导出完整 JSON，备份到手机的共享文件位置或电脑，保留原始文件；在新工程做独立 `applicationId` 的 debug/beta 安装，避免覆盖旧版。
2. **前置检查**：检查 JSON 大小、格式、`sessions/cardio/body` 类型、`completedAt`；计算 SHA-256 保存到 `data_import_log`，识别同一文件重复导入。
3. **映射**：`day`（mon/tue/thu/sat）→ 预设 `plan_day`；`exerciseId` 保留稳定 ID；每个日期会话生成确定性 UUID/旧 ID 映射；旧组 `weight kg → weightGrams`、次数转换为 Int 并验证上下界；缺失/空值保持 null，未完成组仍保留。
4. **完整导入**：一个 Room 事务内写入预设动作、计划、场次、session_exercise、set、cardio、body 和 import_log；失败整个回滚；正式记录默认追加，发现冲突先预览并选择跳过/保留两份/按规则合并。
5. **导入对账**：展示源与目标的场次数、已完成组数、总训练量、最近体重/腰围、训练日时间；抽检最早、最近和含改动的训练场次；确保 JSON 中不认识的字段另存原始导入文件以备将来恢复。
6. **可逆原则**：至少在 V5 验收前保留旧版 App、原始 JSON 及导入日志；V5 安装失败、导入失败不影响 V4.x；绝不要求用户先卸载旧版。

**签名与包名**：如果未来计划原地覆盖 `com.fitwave.personal`，则必须使用相同签名证书、合法递增的 `versionCode`，并处理新老 App 数据/私有目录的迁移。调试阶段更推荐独立 `com.fitwave.personal.v5.dev` 包名让 V4 与 V5 并存；不能保证不同签名的 APK 可以直接覆盖。

## 9. 分阶段里程碑与验收

### 阶段 0：技术验证（必须先过）
- [ ] 新 Android 原生应用在小米 15 Pro 编译、运行、安装成功，显示 Compose A1 主题首页。
- [ ] Room 初始化、演示写入/读取和版本迁移测试通过。
- [ ] 旧 V4.x 示例 JSON 导入 0 丢失、重复导入不增加记录。
- [ ] Android Overlay 最小 PoC（授权、隐藏、停止、重启行为）。
- [ ] Live2D Native/Java 的 Android OS 版本与透明渲染兼容性 PoC；必要时使用 Sprite 后备方案。

### V5.0 Alpha：训练闭环（最优先）
- [ ] 今日训练计划、动作管理、逐组打卡、撤销/编辑、上次重量预填。
- [ ] 训练中断后可续练；每组立即落盘，强杀进程后已确认记录仍存在。
- [ ] 休息倒计时、历史训练、PR/估算 1RM、体重/腰围、有氧、JSON 导入导出。
- [ ] 单元测试：总训练量、PR、日期边界、重复结束、无效输入。

### V5.0 Beta：Android 系统集成
- [ ] 原生桌宠启停、拖动、吸边、权限拒绝降级、控制通知。
- [ ] 训练状态→悬浮卡快照联动；解锁/旋屏/切应用/锁屏及系统重启验证。
- [ ] Glance 桌面小组件（今日计划/本周进度/点按打开）；耗电与发热观察。

### V5.0 RC：角色制作与互动
- [ ] A1 真实分层资产、模型绑定和合法 runtime；动画状态驱动完整。
- [ ] 眨眼、头转、发丝物理、说话口型、表情、训练庆祝；异常降级。
- [ ] 桌宠透明显示、内存泄露和 GPU 占用/帧率限制回归测试。

### V5.0 GA：可选云端与 AI
- [ ] Supabase 身份认证、RLS、离线同步队列、冲突确认和恢复。
- [ ] 点击语音识别、TTS、中断/取消、AI 问答；API 密钥只在服务端。
- [ ] 长时间使用稳定性、数据恢复演练、Release 签名、回归测试、隐私说明。

**禁止越级**：Alpha 的训练记录不稳定，就不把 Live2D 或 AI 视为主线开发完成。

## 10. 质量门槛

- 自动：编译、ktlint/格式检查、静态分析、Unit Test、Room Migration Test、Compose UI Test；CI 构建 Debug 包。
- 数据：随机/损坏/旧版 JSON 输入不能导致部分脏写；重复导入不会重复增加会话；删除与覆盖必须显式确认。
- 性能（目标而非已达到）：首屏响应流畅、训练操作无可感知阻塞；闲置桌宠不得持续高帧率渲染；通过 Android Studio Profiler 实测内存、GPU 和电量，再设合理预算。
- 系统：Android 14+/15+/16 中悬浮权限、前台服务类型、通知权限、后台启动限制，按实际目标 SDK 配置；不要靠关闭 Play Protect 或启用无障碍功能规避系统保护。
- 升级：旧版签名与数据备份安全；数据库 schema 跟踪、回滚路径可执行。
- 受测设备：小米 15 Pro 为主，至少增加一个 Android Emulator 对照；设备系统升级后要重测桌宠和通知。

## 11. 建议的首个开发任务（Codex）

> 新建全新原生 Android 工程 `FitWaveV5`，使用 Kotlin、Jetpack Compose Material 3、MVVM/UDF、Hilt、Room、Coroutines/StateFlow。Android only。首先实现 A1 奶霜丝带设计系统、首页、V3.2 四分化预设课表、训练逐组记录和 Room 数据持久化。创建 `LegacyWorkoutImporter`，从 SAF 文件选择器导入 V4.x `forge_training_v33_2026` 格式 JSON，包含校验、事务写入、幂等检测、导入前后数据对账。配置独立 beta applicationId，不覆盖已安装 V4.x。添加 Room DAO 和纯业务单元测试、Compose 核心流程 UI 测试。第一阶段不接入云端、AI、Live2D 或持续悬浮窗；先提供可运行 APK、工程源码、迁移单测和验收清单。禁止把原 HTML/Vite/Capacitor 代码复制为新主应用。

## 12. 官方技术参考

- Android architecture: https://developer.android.com/topic/architecture/recommendations
- Offline-first data layer: https://developer.android.com/topic/architecture/data-layer/offline-first
- Modularization: https://developer.android.com/topic/modularization/patterns
- Google reference app: https://github.com/android/nowinandroid
- Room / migrations: https://developer.android.com/training/data-storage/room/index.html
- Android foreground service restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Glance: https://developer.android.com/develop/ui/compose/glance
- Live2D support matrix: https://docs.live2d.com/en/cubism-sdk-manual/platform/
- Live2D SDK licenses: https://www.live2d.com/en/sdk/license/
