const { chromium } = require('playwright')
const path = require('path')

;(async () => {
  const browser = await chromium.launch({ channel: 'chromium' })
  const ctx = await browser.newContext({
    viewport: { width: 360, height: 791 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    locale: 'zh-CN',
    colorScheme: 'dark',
  })
  const page = await ctx.newPage()
  await page.goto('http://localhost:8192/', { waitUntil: 'networkidle' })
  await page.evaluate(() => {
    localStorage.setItem('chat-demo:onboarded', 'true')
    localStorage.setItem('chat-demo:api-key', 'sk-shot')
  })
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(900)
  // 打开侧边栏
  await page.locator('[aria-label="打开边栏"]').first().click()
  await page.waitForTimeout(600)
  await page.screenshot({ path: path.resolve(__dirname, '../build/shots/ours-sider.png') })
  // 点设置
  await page.getByText('设置', { exact: true }).first().click()
  await page.waitForTimeout(700)
  await page.screenshot({ path: path.resolve(__dirname, '../build/shots/ours-settings.png') })
  console.log('截图完成')
  await browser.close()
})().catch((e) => { console.error(e); process.exit(1) })
