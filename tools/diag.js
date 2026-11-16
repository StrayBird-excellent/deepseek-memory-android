/** 诊断脚本：检查 Markdown 渲染结果与输入区折叠状态 */

const { chromium } = require('playwright')
const path = require('path')

const BASE = 'http://localhost:8192/'
const DAY = 86400000
const now = Date.now()

const session = {
  id: 'diag-session',
  title: '诊断会话',
  createdAt: now - DAY,
  updatedAt: now,
  rootMessageId: 'u1',
  messages: [
    { id: 'u1', role: 'user', content: '测试问题', files: [], createdAt: now - DAY, parentId: null, selectedChildId: 'a1' },
    {
      id: 'a1', role: 'assistant', createdAt: now - DAY + 1000, model: 'deepseek-v4-pro',
      thinking: true, search: false, prefix: true, status: 'done', parentId: 'u1',
      blocks: [
        { type: 'thinking', thinking: '这是思考内容，应当折叠起来。', signature: 's', startedAt: now - 5000, endedAt: now - 1000 },
        { type: 'text', text: '普通段落一。' },
        { type: 'text', text: '## 标题测试\n\n标题后的段落。' },
      ],
    },
  ],
}

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
  await page.goto(BASE, { waitUntil: 'networkidle' })
  await page.evaluate(async (s) => {
    localStorage.setItem('chat-demo:onboarded', 'true')
    localStorage.setItem('chat-demo:api-key', 'sk-diag')
    await new Promise((resolve, reject) => {
      const req = indexedDB.open('chat-demo', 11)
      req.onupgradeneeded = () => {
        const db = req.result
        for (const n of ['sessions', 'files', 'sessionsStaging', 'filesStaging'])
          if (!db.objectStoreNames.contains(n)) db.createObjectStore(n, { keyPath: 'id' })
        if (!db.objectStoreNames.contains('sessionSummaries'))
          db.createObjectStore('sessionSummaries', { keyPath: 'id' })
      }
      req.onerror = () => reject(req.error)
      req.onsuccess = () => {
        const db = req.result
        const tx = db.transaction(['sessions', 'sessionSummaries'], 'readwrite')
        tx.objectStore('sessions').put(s)
        tx.objectStore('sessionSummaries').put({
          id: s.id, title: s.title, createdAt: s.createdAt, updatedAt: s.updatedAt, fileIds: [],
        })
        tx.oncomplete = () => resolve()
        tx.onerror = () => reject(tx.error)
      }
    })
  }, session)
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  await page.locator('[aria-label="打开边栏"]').first().click()
  await page.waitForTimeout(400)
  await page.getByText('诊断会话', { exact: true }).first().click()
  await page.waitForTimeout(1000)

  const md = await page.evaluate(() => {
    const root = document.querySelector('[class*="messageList"], main') || document.body
    const h2 = root.querySelector('h2')
    const bodyText = root.innerText || ''
    return {
      hasH2: !!h2,
      h2Text: h2?.textContent ?? null,
      h2Style: h2 ? getComputedStyle(h2).fontSize + ' / ' + getComputedStyle(h2).fontWeight : null,
      showsRawHashes: bodyText.includes('##'),
      excerpt: bodyText.slice(0, 220),
    }
  })
  console.log('=== Markdown 渲染 ===')
  console.log(JSON.stringify(md, null, 2))

  const input = await page.evaluate(() => {
    const toggles = [...document.querySelectorAll('button')].filter((b) =>
      /深度思考|智能搜索|Deep Think|Smart Search/i.test(b.textContent || '')
    )
    const wrapper = toggles[0]?.closest('div[class]')?.parentElement?.parentElement
    const rect = wrapper?.getBoundingClientRect()
    return {
      toggleCount: toggles.length,
      toggleLabels: toggles.map((t) => (t.textContent || '').trim()),
      toggleWidths: toggles.map((t) => Math.round(t.getBoundingClientRect().width)),
      labelsHidden: toggles.map((t) => {
        const span = t.querySelector('span')
        return span ? getComputedStyle(span).display : 'n/a'
      }),
      wrapperWidth: rect ? Math.round(rect.width) : null,
    }
  })
  console.log('\n=== 输入区按钮 ===')
  console.log(JSON.stringify(input, null, 2))

  await browser.close()
})().catch((e) => {
  console.error(e)
  process.exit(1)
})
