/**
 * 交互测试：复现并验证「输入框点不动 / 点小眼睛丢内容」两个问题。
 *
 * 用 Chromium 的移动端上下文跑真实点击与键盘输入，
 * 断言输入值、焦点、input 类型在交互前后保持一致。
 */

const { chromium } = require('playwright')
const path = require('path')
const fs = require('fs')

const BASE = 'http://localhost:8190/'
const OUT = path.resolve(__dirname, '../build/shots')

const results = []
const check = (name, pass, detail = '') => {
  results.push({ name, pass, detail })
  console.log(`${pass ? '✓' : '✗'} ${name}${detail ? ' — ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'chromium' })
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    locale: 'zh-CN',
    colorScheme: 'dark',
    userAgent:
      'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36',
  })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))

  await page.goto(BASE, { waitUntil: 'networkidle' })
  await page.waitForTimeout(500)

  const input = page.locator('#onboarding-key')

  // 1) 点击输入框能否聚焦
  await input.click()
  await page.waitForTimeout(200)
  let focused = await page.evaluate(() => document.activeElement?.id ?? '')
  check('点击输入框可获得焦点', focused === 'onboarding-key', `activeElement=${focused || '无'}`)

  // 2) 逐字输入
  const sample = 'sk-abc123XYZ789defGHI456'
  await page.keyboard.type(sample, { delay: 20 })
  await page.waitForTimeout(200)
  let value = await input.inputValue()
  check('键盘输入完整写入', value === sample, `期望 ${sample.length} 字符，实际 ${value.length}`)

  // 3) input 恒为 text，不触发系统密码管理器
  const typeAttr = await input.getAttribute('type')
  check('input 类型恒为 text', typeAttr === 'text', `type=${typeAttr}`)

  // 4) 遮蔽态生效
  const security = await input.evaluate((el) => getComputedStyle(el).webkitTextSecurity)
  check('未显示时文字被遮蔽', security === 'disc', `-webkit-text-security=${security}`)

  await page.screenshot({ path: path.join(OUT, 'test-01-typed.png') })

  // 5) 点小眼睛 —— 关键回归点
  await page.locator('[aria-label="显示 API Key"]').click()
  await page.waitForTimeout(300)
  value = await input.inputValue()
  check('点「显示」后内容不丢失', value === sample, `实际 ${value.length} 字符`)
  const securityShown = await input.evaluate((el) => getComputedStyle(el).webkitTextSecurity)
  check('显示后取消遮蔽', securityShown === 'none' || securityShown === '', `security=${securityShown}`)
  await page.screenshot({ path: path.join(OUT, 'test-02-revealed.png') })

  // 6) 再点回隐藏
  await page.locator('[aria-label="隐藏 API Key"]').click()
  await page.waitForTimeout(300)
  value = await input.inputValue()
  check('再点「隐藏」内容仍不丢失', value === sample, `实际 ${value.length} 字符`)

  // 7) 连续切换三次做压力测试
  for (let i = 0; i < 3; i++) {
    await page.locator('[aria-label="显示 API Key"]').click()
    await page.waitForTimeout(120)
    await page.locator('[aria-label="隐藏 API Key"]').click()
    await page.waitForTimeout(120)
  }
  value = await input.inputValue()
  check('连续切换 3 轮后内容不变', value === sample, `实际 ${value.length} 字符`)

  // 8) 切换后焦点仍在输入框，键盘不会收起
  focused = await page.evaluate(() => document.activeElement?.id ?? '')
  check('切换遮蔽后焦点仍在输入框', focused === 'onboarding-key', `activeElement=${focused || '无'}`)

  // 9) 继续输入追加字符，验证输入法状态没被打断
  await page.keyboard.type('TAIL', { delay: 15 })
  await page.waitForTimeout(150)
  value = await input.inputValue()
  check('切换后继续输入正常', value === sample + 'TAIL', `实际长度 ${value.length}`)

  // 10) 模型卡片可点选
  await page.getByText('DeepSeek V4 Pro', { exact: true }).click()
  await page.waitForTimeout(200)
  const checked = await page.locator('[role="radio"][aria-checked="true"]').innerText()
  check('模型卡片可切换', checked.includes('V4 Pro'), checked.split('\n')[0])

  // 11) 提交非法 key 应给出错误而不是静默失败
  await page.locator('button:has-text("确认并进入")').click()
  await page.waitForTimeout(3500)
  const hasError = await page.locator('[role="alert"]').count()
  check('无效 Key 会显示错误提示', hasError > 0, hasError ? '已提示' : '无提示')
  await page.screenshot({ path: path.join(OUT, 'test-03-invalid-key.png') })

  await browser.close()

  const failed = results.filter((r) => !r.pass)
  console.log(`\n结果：${results.length - failed.length}/${results.length} 通过`)
  if (errors.length) {
    console.log('页面异常:', errors.slice(0, 5))
  }
  process.exit(failed.length === 0 ? 0 : 1)
})().catch((e) => {
  console.error('测试异常:', e)
  process.exit(2)
})
