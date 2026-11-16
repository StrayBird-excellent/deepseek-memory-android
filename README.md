# DeepSeek 记忆版（Android）

基于 DeepSeek 官方 [chat-demo](https://github.com/deepseek-ai/chat-demo) 二次开发的 Android 客户端。在官方静态版基础上增加了本地长期记忆、打赏入口、加号附件菜单，以及针对 Android 各类屏幕和系统的兼容性处理。

> **免责声明**：本项目是个人学习与二次开发作品，与 DeepSeek 官方无关。应用直接调用 DeepSeek 开放 API，需用户自行提供 API Key。项目名称与图标沿用原 chat-demo，仅用于个人研究，如需公开发布请自行处理品牌与授权问题。

## 主要功能

- 多会话管理：自动标题、重命名、删除，编辑提问、重新生成、消息分支切换
- 流式回复、深度思考、智能搜索、链接阅读与引用
- 本地长期记忆（基于 Ebbinghaus 遗忘曲线）
- 附件上传（拍照 / 相册 / 文件）、语音输入、朗读
- Markdown、代码高亮、KaTeX 数学公式、Mermaid 图表
- 中英双语、浅色/深色主题、历史导入导出（DeepSeek 官方 ZIP 格式）
- 打赏入口（二维码）
- Android 兼容：低版本 WebView polyfill、横屏/平板/折叠屏移动布局、刘海屏安全区、键盘自适应

## 项目结构

```
.
├── app/          # Web 前端（React + TypeScript + Rspack）+ Capacitor Android 壳
│   ├── src/      # 应用源码
│   ├── android/  # Capacitor 原生工程（正式版 apk 由此产出）
│   └── public/   # 静态资源（打赏二维码、品牌 Logo）
├── native/       # 独立的 Jetpack Compose 原生版（实验性）
├── reference/    # 官方 chat-demo 参考副本与官方图标资源（仅供对照）
└── tools/        # 构建、截图、图标生成、CSS 补丁等脚本
```

## 构建

### 环境

- Node.js 22+、pnpm
- JDK 21（`D:\jdk21`）
- Android SDK 36（`D:\android-sdk`）

路径在 `tools/build_apk.sh` 顶部配置，按需修改为本机路径。

### Debug 包

```sh
bash tools/build_apk.sh
# 或指定版本：APK_VERSION=2.1 bash tools/build_apk.sh
```

产物在 `build/DeepSeek-Memory-v<版本>-debug.apk`。

### 正式版（需签名）

正式版使用 `app/android/keystore/` 下的签名证书。**该目录已被 .gitignore 排除**——如需自己发布，请自行生成 keystore 并创建 `keystore.properties`：

```properties
storeFile=keystore/your-release.jks
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

生成 keystore 示例：

```sh
keytool -genkeypair -v -keystore app/android/keystore/your-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias your_alias
```

然后：

```sh
APK_VERSION=1.0 bash tools/build_apk.sh release
```

**签名证书务必备份，丢失后将无法为应用发布更新。**

## 隐私与数据

- 无后端服务：请求由 WebView 直接发往 `api.deepseek.com`
- API Key 与设置存于 localStorage；对话与附件原件存于 IndexedDB
- 数据仅存本机，随应用卸载清除
- 不收集、不上传任何用户数据到本项目作者的服务器（本项目没有服务器）

## 开源协议

原 chat-demo 采用 MIT 协议，本项目沿用。详见 [app/LICENSE](app/LICENSE)。
