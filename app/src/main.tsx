// 兼容层必须最先执行：polyfill 低版本 WebView 缺失的 API
import './app/compat.js'
import { installCompat } from './app/compat.js'
import { createRoot } from 'react-dom/client'
import { App } from './app/App.js'
import { setupTheme } from './app/theme.js'
import { ensureKatex } from './services/markdown/katex.js'
import './app/theme.css'
import './app/global.css'

installCompat()
ensureKatex()
setupTheme()

// 原生壳内标记 html，让 CSS 断点在平板/折叠屏/横屏下也强制走移动布局，
// 与 useIsMobile 的返回值保持一致（APK 始终是触屏设备，不需要桌面版左侧栏）。
if (typeof window !== 'undefined') {
  const cap = (window as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor
  if (cap?.isNativePlatform?.()) {
    document.documentElement.classList.add('native-shell')
  }
}

createRoot(document.getElementById('root') as HTMLElement).render(<App />)
