/**
 * 界面验证脚本：用移动端视口渲染应用，逐步截图。
 *
 * 不依赖真实 API Key：引导页直接截；主界面与记忆面板通过注入 localStorage /
 * IndexedDB 数据构造出「已经用了一段时间」的状态，专门用来看 UI 与记忆强度的呈现。
 */

const { chromium } = require('playwright')
const path = require('path')
const fs = require('fs')

const BASE = 'http://localhost:8190/'
const OUT = path.resolve(__dirname, '../build/shots')

/** 构造几条处于不同记忆强度阶段的条目，覆盖清晰 / 模糊 / 已淡忘 / 锁定 */
const now = Date.now()
const DAY = 86400000
const seedMemories = [
  {
    id: 'm1',
    sessionId: '__SESSION__',
    kind: 'persona',
    text: '对方希望我叫她「阿岚」，说话时带一点旧友的随意，不用敬语',
    keywords: ['阿岚', '称呼', '语气'],
    stability: 62,
    difficulty: 0.15,
    reps: 9,
    lapses: 0,
    createdAt: now - 40 * DAY,
    lastReviewAt: now - 2 * DAY,
    sourceMessageIds: [],
    pinned: true,
    archived: false,
    manual: false,
    importance: 0.95,
  },
  {
    id: 'm2',
    sessionId: '__SESSION__',
    kind: 'fact',
    text: '对方在杭州做前端，主要写 TypeScript，最近在从零搭一个设计系统',
    keywords: ['杭州', '前端', 'TypeScript', '设计系统'],
    stability: 18,
    difficulty: 0.3,
    reps: 4,
    lapses: 0,
    createdAt: now - 12 * DAY,
    lastReviewAt: now - 1 * DAY,
    sourceMessageIds: [],
    pinned: false,
    archived: false,
    manual: false,
    importance: 0.8,
  },
  {
    id: 'm3',
    sessionId: '__SESSION__',
    kind: 'preference',
    text: '不喜欢长篇铺垫，回答要直接给结论，例子可以省',
    keywords: ['简洁', '直接', '结论'],
    stability: 9,
    difficulty: 0.4,
    reps: 2,
    lapses: 0,
    createdAt: now - 9 * DAY,
    lastReviewAt: now - 3 * DAY,
    sourceMessageIds: [],
    pinned: false,
    archived: false,
    manual: false,
    importance: 0.7,
  },
  {
    id: 'm4',
    sessionId: '__SESSION__',
    kind: 'event',
    text: '上个月提过要在九月前把旧项目迁到新仓库，后来因为排期推迟了',
    keywords: ['迁移', '仓库', '排期'],
    stability: 6,
    difficulty: 0.55,
    reps: 1,
    lapses: 0,
    createdAt: now - 20 * DAY,
    lastReviewAt: now - 9 * DAY,
    sourceMessageIds: [],
    pinned: false,
    archived: false,
    manual: false,
    importance: 0.5,
  },
  {
    id: 'm5',
    sessionId: '__SESSION__',
    kind: 'summary',
    text: '早期聊过一阵子写作节奏的问题，结论是先固定每天两小时、不追求字数，把修改留到第二天再看',
    keywords: [],
    stability: 4,
    difficulty: 0.5,
    reps: 0,
    lapses: 0,
    createdAt: now - 26 * DAY,
    lastReviewAt: now - 16 * DAY,
    sourceMessageIds: [],
    pinned: false,
    archived: true,
    manual: false,
    importance: 0.6,
  },
  {
    id: 'm6',
    sessionId: '__SESSION__',
    kind: 'preference',
    text: '手写的一条：别在回答末尾追问「还需要我做什么吗」',
    keywords: [],
    stability: 30,
    difficulty: 0.15,
    reps: 0,
    lapses: 0,
    createdAt: now - 3 * DAY,
    lastReviewAt: now - 3 * DAY,
    sourceMessageIds: [],
    pinned: false,
    archived: false,
    manual: true,
    importance: 0.9,
  },
]

/** 一段像样的对话，用来检查消息气泡、思考块、引用等排版 */
const seedSession = {
  id: 'seed-session',
  title: '设计系统的暗色模式',
  createdAt: now - 5 * DAY,
  updatedAt: now - 3600000,
  rootMessageId: 'u1',
  messages: [
    {
      id: 'u1',
      role: 'user',
      content: '我们设计系统要加暗色模式，颜色 token 应该怎么分层比较合理？',
      files: [],
      createdAt: now - 5 * DAY,
      parentId: null,
      selectedChildId: 'a1',
    },
    {
      id: 'a1',
      role: 'assistant',
      createdAt: now - 5 * DAY + 20000,
      model: 'deepseek-v4-pro',
      thinking: true,
      search: false,
      prefix: true,
      status: 'done',
      parentId: 'u1',
      blocks: [
        {
          type: 'thinking',
          thinking:
            '对方在做设计系统，问的是 token 分层。应该先讲清语义层和原始层的区别，再谈暗色模式下哪些层需要反转、哪些不能反转。',
          signature: 'sig',
          startedAt: now - 5 * DAY + 1000,
          endedAt: now - 5 * DAY + 9000,
        },
        {
          type: 'text',
          text: '分三层，从下往上：\n\n**原始层**（primitive）只放色值，不带语义，比如 `blue-500`、`neutral-900`。这层在明暗两套主题里是同一份，永远不改。\n\n**语义层**（semantic）是真正被业务引用的那一层，比如 `bg-base`、`label-primary`、`border-l2`。这一层才承载「在暗色下要变成什么」的决策。\n\n**组件层**（component）只在必要时存在，比如 `button-primary-fill`，它指向语义层而不是原始层。\n\n关键点：**只让语义层感知主题**。原始层保持绝对稳定，组件层保持绝对抽象。这样加一套暗色主题只需要重写语义层的一张映射表，业务代码一行不用动。',
        },
      ],
    },
    {
      id: 'u2',
      role: 'user',
      content: '那暗色下的阴影怎么处理？直接调透明度好像效果不对。',
      files: [],
      createdAt: now - 3600000,
      parentId: 'a1',
      selectedChildId: 'a2',
    },
    {
      id: 'a2',
      role: 'assistant',
      createdAt: now - 3600000 + 15000,
      model: 'deepseek-v4-pro',
      thinking: false,
      search: false,
      prefix: true,
      status: 'done',
      parentId: 'u2',
      blocks: [
        {
          type: 'text',
          text: '暗色下黑色阴影几乎不可见，因为它比背景还亮不了多少。两个替代思路：\n\n1. **用边框代替阴影**——暗色里 1px 的浅色描边比投影更能区分层级，`border-l2` 就是干这个的。\n2. **用亮度台阶代替投影**——层级靠背景色的亮度差表达，比如 `bg-base` 到 `bg-layer-2` 到 `bg-layer-3` 逐级提亮。\n\n真要用阴影时，暗色下把它换成**带一点冷调的深色 + 更大扩散**，而不是单纯加深黑色。',
        },
      ],
    },
  ],
}

async function shot(page, name, options = {}) {
  const file = path.join(OUT, `${name}.png`)
  await page.screenshot({ path: file, ...options })
  console.log('✓', name, '->', file)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  // 使用完整 Chromium 的新版 headless 模式，避免额外下载 headless shell
  const browser = await chromium.launch({ channel: 'chromium' })

  // ---------- 移动端 ----------
  const mobile = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
    locale: 'zh-CN',
    colorScheme: 'dark',
    userAgent:
      'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36',
  })
  const page = await mobile.newPage()
  const errors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text())
  })
  page.on('pageerror', (err) => errors.push(String(err)))

  await page.goto(BASE, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await shot(page, '01-onboarding-dark', { fullPage: true })

  // 浅色主题下的引导页，确认明暗两套变量都成立
  await page.emulateMedia({ colorScheme: 'light' })
  await page.waitForTimeout(400)
  await shot(page, '02-onboarding-light', { fullPage: true })
  await page.emulateMedia({ colorScheme: 'dark' })

  // ---------- 注入「已完成引导 + 已有会话与记忆」的状态 ----------
  await page.evaluate(
    async ({ session, memories }) => {
      localStorage.setItem('chat-demo:onboarded', 'true')
      localStorage.setItem('chat-demo:api-key', 'sk-verification-only')
      localStorage.setItem('chat-demo:model', 'deepseek-v4-pro')
      localStorage.setItem('chat-demo:thinking', 'true')

      // 会话写入官方库
      await new Promise((resolve, reject) => {
        const req = indexedDB.open('chat-demo', 11)
        req.onupgradeneeded = () => {
          const db = req.result
          for (const name of ['sessions', 'files', 'sessionsStaging', 'filesStaging']) {
            if (!db.objectStoreNames.contains(name)) {
              db.createObjectStore(name, { keyPath: 'id' })
            }
          }
          if (!db.objectStoreNames.contains('sessionSummaries')) {
            db.createObjectStore('sessionSummaries', { keyPath: 'id' })
          }
        }
        req.onerror = () => reject(req.error)
        req.onsuccess = () => {
          const db = req.result
          const tx = db.transaction(['sessions', 'sessionSummaries'], 'readwrite')
          tx.objectStore('sessions').put(session)
          tx.objectStore('sessionSummaries').put({
            id: session.id,
            title: session.title,
            createdAt: session.createdAt,
            updatedAt: session.updatedAt,
            fileIds: [],
          })
          tx.oncomplete = () => resolve()
          tx.onerror = () => reject(tx.error)
        }
      })

      // 记忆写入记忆库
      await new Promise((resolve, reject) => {
        const req = indexedDB.open('deepseek-memory', 1)
        req.onupgradeneeded = () => {
          const db = req.result
          if (!db.objectStoreNames.contains('memories')) {
            const store = db.createObjectStore('memories', { keyPath: 'id' })
            store.createIndex('sessionId', 'sessionId')
          }
          if (!db.objectStoreNames.contains('configs')) {
            db.createObjectStore('configs', { keyPath: 'sessionId' })
          }
          if (!db.objectStoreNames.contains('progress')) {
            db.createObjectStore('progress', { keyPath: 'sessionId' })
          }
        }
        req.onerror = () => reject(req.error)
        req.onsuccess = () => {
          const db = req.result
          const tx = db.transaction(['memories', 'configs'], 'readwrite')
          for (const memory of memories) {
            tx.objectStore('memories').put(memory)
          }
          tx.objectStore('configs').put({
            sessionId: session.id,
            autoExtract: true,
            maxEntries: 24,
            tokenBudget: 3000,
            compactThreshold: 0.8,
          })
          tx.oncomplete = () => resolve()
          tx.onerror = () => reject(tx.error)
        }
      })
    },
    {
      session: seedSession,
      memories: seedMemories.map((m) => ({ ...m, sessionId: seedSession.id })),
    }
  )

  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await shot(page, '03-welcome-dark')

  // 移动端会话列表在抽屉里：先打开边栏，再点会话
  const siderBtn = page.locator('[aria-label="打开边栏"]').first()
  if (await siderBtn.count()) {
    await siderBtn.click()
    await page.waitForTimeout(600)
    await shot(page, '04-sider-dark')
  }
  await page.getByText('设计系统的暗色模式', { exact: true }).first().click()
  await page.waitForTimeout(1000)
  await shot(page, '04-session-dark')

  // 打开记忆面板
  const memoryBtn = page.locator('[aria-label="记忆"]').first()
  if (await memoryBtn.count()) {
    await memoryBtn.click()
    await page.waitForTimeout(700)
    await shot(page, '05-memory-panel-dark')

    // 滚到面板底部看开关与添加区
    await page.evaluate(() => {
      const list = document.querySelector('[role="dialog"] > div:nth-child(4)')
      if (list) list.scrollTop = list.scrollHeight
    })
    await page.waitForTimeout(300)
    await shot(page, '06-memory-panel-bottom')

    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
  } else {
    console.log('!! 记忆按钮未找到')
  }

  await shot(page, '07-session-light', {})
  await mobile.close()

  // ---------- 桌面端 ----------
  const desktop = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'zh-CN',
    colorScheme: 'dark',
  })
  const page2 = await desktop.newPage()
  await page2.addInitScript(
    ({ session, memories }) => {
      localStorage.setItem('chat-demo:onboarded', 'true')
      localStorage.setItem('chat-demo:api-key', 'sk-verification-only')
      localStorage.setItem('chat-demo:model', 'deepseek-v4-pro')
      void session
      void memories
    },
    { session: seedSession, memories: seedMemories }
  )
  await page2.goto(BASE, { waitUntil: 'networkidle' })
  await page2.waitForTimeout(1200)
  await shot(page2, '08-desktop-welcome')
  await desktop.close()

  await browser.close()

  if (errors.length) {
    console.log('\n=== 控制台错误 ===')
    for (const err of errors.slice(0, 10)) console.log(' -', err)
  } else {
    console.log('\n控制台无错误')
  }
})().catch((error) => {
  console.error('截图失败:', error)
  process.exit(1)
})
