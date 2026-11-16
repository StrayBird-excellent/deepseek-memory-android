import type { CapacitorConfig } from '@capacitor/cli'

/**
 * Capacitor 配置。
 *
 * 应用完全静态：所有请求由 WebView 直接发往 api.deepseek.com，
 * 没有中转后端，因此不存在服务端保存用户数据的可能。
 * 数据落在 WebView 的 IndexedDB 与 localStorage 中，随应用卸载一并清除。
 */
const config: CapacitorConfig = {
  appId: 'com.memoseek.app',
  appName: 'DeepSeek',
  webDir: 'dist',
  android: {
    // 必须关掉。开启后 Capacitor 会用 BaseInputConnection(webView, false) 替换
    // WebView 自己的 Chromium InputConnection，那个实现没有 Editable，
    // 输入法的 commitText / setComposingText 全部返回 false：
    // 中文只能靠按键事件挤进第一个字，后面全部丢失，且不触发 input 事件
    // （React 拿不到值，发送按钮也不出现）。英文按键走 KeyEvent 路径，所以看起来正常。
    captureInput: false,
    // 发布包关闭远程调试；需要排查问题时改回 true 并重新构建
    webContentsDebuggingEnabled: false,
    allowMixedContent: false,
  },
  server: {
    // 默认即为 https://localhost，这里写明以便后续排查来源相关问题时对照
    androidScheme: 'https',
  },
}

export default config
