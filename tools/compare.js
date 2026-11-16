/**
 * 与官方 App 同尺寸对比。
 *
 * 用 360x791dp（官方设备实测）渲染我们的会话页，
 * 再把官方截图并排拼接，直接看布局与间距的差距。
 */

const { chromium } = require('playwright')
const path = require('path')
const fs = require('fs')

const BASE = 'http://localhost:8192/'
const OUT = path.resolve(__dirname, '../build/shots')
const DAY = 86400000
const now = Date.now()

// 内容尽量贴近官方截图里的那段对话，便于直接比对行距和段落间距
const session = {
  id: 'compare-session',
  title: '多设备并行推理方案',
  createdAt: now - 3 * DAY,
  updatedAt: now - 3600000,
  rootMessageId: 'u1',
  messages: [
    {
      id: 'u1',
      role: 'user',
      content: '我有个问题 怎么让多台设备并行做模型推理',
      files: [],
      createdAt: now - 3 * DAY,
      parentId: null,
      selectedChildId: 'a1',
    },
    {
      id: 'a1',
      role: 'assistant',
      createdAt: now - 3 * DAY + 20000,
      model: 'deepseek-v4-pro',
      thinking: true,
      search: false,
      prefix: true,
      status: 'done',
      parentId: 'u1',
      blocks: [
        {
          type: 'thinking',
          thinking: '先判断瓶颈在哪：显存装不下，还是算力不够。',
          signature: 'sig',
          startedAt: now - 3 * DAY + 1000,
          endedAt: now - 3 * DAY + 4000,
        },
        {
          type: 'text',
          text: '让多台设备并行做模型推理，核心思路取决于你的瓶颈在哪：是单台设备显存装不下整个模型，还是单台设备算不过来大量请求。针对这两种情况，主流方案分为"模型并行"和"数据并行"两条路径。',
        },
        {
          type: 'text',
          text: '## 🧩 路径一：单模型太大，用"模型并行"拆分\n\n当一个模型（如70B+参数的大语言模型）无法装入单张GPU或单台边缘设备时，需要将模型本身拆开，让多台设备协作运行同一个模型副本。\n\n**张量并行（Tensor Parallelism, TP）**\n\n将模型同一层内的权重矩阵切分到不同设备，每个设备计算同一请求的部分结果，再通过高速互联（如 NVLink）汇总。',
        },
      ],
    },
  ],
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'chromium' })
  const ctx = await browser.newContext({
    viewport: { width: 360, height: 791 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    locale: 'zh-CN',
    colorScheme: 'dark',
    userAgent:
      'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36',
  })
  const page = await ctx.newPage()

  await page.goto(BASE, { waitUntil: 'networkidle' })
  await page.evaluate(async (s) => {
    localStorage.setItem('chat-demo:onboarded', 'true')
    localStorage.setItem('chat-demo:api-key', 'sk-compare')
    localStorage.setItem('chat-demo:model', 'deepseek-v4-pro')
    await new Promise((resolve, reject) => {
      const req = indexedDB.open('chat-demo', 11)
      req.onupgradeneeded = () => {
        const db = req.result
        for (const name of ['sessions', 'files', 'sessionsStaging', 'filesStaging']) {
          if (!db.objectStoreNames.contains(name)) db.createObjectStore(name, { keyPath: 'id' })
        }
        if (!db.objectStoreNames.contains('sessionSummaries')) {
          db.createObjectStore('sessionSummaries', { keyPath: 'id' })
        }
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
  await page.waitForTimeout(1000)

  // 打开会话
  await page.locator('[aria-label="打开边栏"]').first().click()
  await page.waitForTimeout(500)
  await page.getByText('多设备并行推理方案', { exact: true }).first().click()
  await page.waitForTimeout(1200)

  // 滚到顶部，让用户气泡与折叠的思考行都进入视野，才能和官方截图逐项对齐
  await page.evaluate(() => {
    for (const el of document.querySelectorAll('*')) {
      const style = getComputedStyle(el)
      if (/auto|scroll/.test(style.overflowY) && el.scrollHeight > el.clientHeight) {
        el.scrollTop = 0
      }
    }
  })
  await page.waitForTimeout(400)

  const info = await page.evaluate(() => ({
    hasThinkTitle: /已思考|思考已停止|深度思考/.test(document.body.innerText),
    excerpt: document.body.innerText.slice(0, 180),
  }))
  console.log('页面文本检查:', JSON.stringify(info))

  await page.screenshot({ path: path.join(OUT, 'compare-ours.png') })
  console.log('✓ 我们的渲染', path.join(OUT, 'compare-ours.png'))

  await browser.close()

  // 并排拼接
  const { execSync } = require('child_process')
  execSync(
    `python "${path.join(__dirname, 'compose_compare.py')}"`,
    { stdio: 'inherit', cwd: path.resolve(__dirname, '..') }
  )
})().catch((e) => {
  console.error('失败:', e)
  process.exit(1)
})
