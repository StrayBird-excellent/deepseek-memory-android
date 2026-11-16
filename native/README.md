# DeepSeek Native

Kotlin + Jetpack Compose 原生实现，视觉与交互对齐官方 Android 客户端，
并在此之上叠加一层本地记忆能力。

独立于仓库根目录的 Web 版（`app/`），两者可同时安装在设备上。

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| UI | Jetpack Compose + Material3 | 官方同款声明式框架 |
| 语言 | Kotlin 2.1.20 | K2 编译器 |
| 构建 | AGP 8.13.0 / Gradle 8.14.3 / JDK 21 | |
| 网络 | OkHttp 4.12 + **okhttp-sse** | SSE 用 Square 官方扩展，非手写分帧 |
| 序列化 | kotlinx-serialization 1.7.3 | 编译期生成，无反射 |
| 存储 | DataStore Preferences | 异步 + Flow |
| SDK | compileSdk 36 / minSdk 24 / targetSdk 36 | |

**不引 Retrofit**：只有两个端点，多一层封装没有收益。
**不引 Room**：会话是文档型数据，全量读写足够；需要全文检索时再迁。
**不引 Hilt**：依赖少，手写构造注入更透明。

## 功能

- 流式对话（SSE），思考过程与正文分离展示
- 深度思考 / 智能搜索开关，与官方一致的双胶囊
- 思考块默认折叠，显示「已思考（用时 N 秒）」
- Markdown 渲染：标题、段落、粗体、行内代码、列表、代码块
- 消息长按复制，失败可重试，最后一条可重新生成
- 会话持久化 + 侧边栏（搜索、按自然日分组、长按删除）
- 主题：深色 / 浅色 / 跟随系统
- 语言：中文 / English / 跟随系统（全部界面文案集中在 `DsStrings`）
- 模型切换：DeepSeek V3 / R1
- 账户：真实余额（官方 `GET /user/balance`），充值跳官方页面
- **记忆层**：自动抽取 + 手动添加、按艾宾浩斯曲线衰减、发送前注入 system prompt

## 目录结构

```
app/src/main/java/com/memoseek/mobile/
├── MainActivity.kt              入口 + 页面切换
├── data/
│   ├── model/                   Message、Conversation、Memory、ModelInfo
│   ├── remote/                  Dto、DeepSeekClient（SSE + 余额）
│   ├── memory/                  MemoryExtractor（规则抽取）
│   └── repository/              Chat / Conversation / Settings / Memory
└── ui/
    ├── i18n/                    DsStrings（中英双语文案）
    ├── theme/                   Color（双色板）· Dimen · Motion · Type · Theme
    ├── components/              CapsuleToggle、ChatInputBar、DsIcons（原子图标）
    ├── home/                    HomeScreen
    ├── chat/                    ChatScreen、ChatViewModel、MarkdownText
    ├── sider/                   SiderContent、ConversationGrouping
    ├── settings/                SettingsScreen、ApiKeyDialog
    └── memory/                  MemoryScreen
```

## 设计 token 来源

`Color.kt` / `Dimen.kt` 的数值来自官方 App 运行时 UI 层级树与像素采样：

| 项 | 官方实测 | 本项目 |
|---|---|---|
| 页面底色 | `#0F0F0F` | 同 |
| 输入容器 | `#242424`，左右 11dp，圆角 20dp，高 97.7dp | 98.0dp |
| 用户气泡 | `#333333`，内边距 15×9 | 同 |
| 正文色 | `#E1E2EC` | 同 |
| 品牌蓝 | `#6799FE` | 同 |
| 功能胶囊 | 高 29.3dp，圆角 15dp | 30dp |
| 顶栏 | 47.7dp | 48dp |
| 正文左留白 | 22dp | 同 |

浅色色板官方未提供截图，按同一语义层级推导，品牌蓝在浅底上加深以保证对比度。

## 国际化

文案集中在 `ui/i18n/DsStrings.kt`，用数据类 + 全局可变状态（与色板同一套机制），
切换语言不需要重建 Activity。

没有用 `strings.xml`：这套界面全部由 Compose 渲染，走资源文件反而要为了拿
Context 到处传参，且语言切换必须走 `AppCompatDelegate` 重建。

错误提示不放在网络层：`DeepSeekClient` 只抛 HTTP 状态码，
由 `ChatViewModel` 按当前语言翻译——网络层不该知道界面说哪国话。

## 记忆层设计

| 环节 | 做法 | 理由 |
|---|---|---|
| 抽取 | 规则匹配（姓名、项目、偏好、职业） | 抽取在发送的同步路径上，加一次模型调用会拖慢首字延迟；且误抽代价远大于漏抽 |
| 权重 | 初始 0.5，每次引用 +0.08，上限 1.0 | 常用信息自然升权 |
| 衰减 | 半衰期 7 天（`0.5^(天数/7)`） | 艾宾浩斯遗忘曲线的工程近似 |
| 淘汰 | 衰减后低于 0.15 清理 | 长期不用的信息让位 |
| 注入 | 取权重最高的 6 条拼进 system prompt | 过多会稀释指令，过少失去上下文 |

注入模板里明确写了「不要主动复述记忆」——否则模型会开头来一句
「我记得你叫小明」，在多轮对话里非常出戏。

## 构建

```bash
bash tools/build_native.sh          # debug
bash tools/build_native.sh release  # release（需先配签名）
```

**注意**：工程位于含中文的路径下，依赖 `gradle.properties` 里的
`android.overridePathCheck=true`。移到纯英文路径后可删除。

体积：debug 18MB（无裁剪），release **1.5MB**（R8 裁掉了未使用的 Material 图标）。

## 已知问题

1. **单元测试无法执行**（`ClassNotFoundException`）
   Kotlin 2.1.20 + AGP 8.13.0 下，`testDebugUnitTest` 的扫描目录不含
   `build/tmp/kotlin-classes/debugUnitTest`。测试代码（202 行，覆盖时间分组边界、
   Markdown 流式容错、记忆抽取规则）已写在 `src/test/kotlin`，环境修复后即可运行。

2. **真机未验证**：流式、持久化、抽屉手势尚未在设备上跑过。

3. **英文文案未在真机校对**：中英双语已接入，但英文长度差异可能撑破个别窄容器（如胶囊）。

## 账户与支付边界

余额走官方接口，仅读不写。充值一律跳官方页面，客户端不接触任何支付信息，
也不做账号登录——本应用的身份凭证就是 API Key。
