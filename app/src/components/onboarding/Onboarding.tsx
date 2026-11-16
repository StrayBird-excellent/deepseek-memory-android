import { useEffect, useRef, useState } from 'react'
import clsx from 'clsx'
import { ApiError, verifyApiKey } from '../../services/api/client.js'
import { setApiKey, setModel, setOnboarded, useSettings } from '../../services/settings.js'
import type { ChatModel } from '../../services/api/types.js'
import { Button } from '../base/Button.js'
import { IconEyeOffOutline16, IconEyeOutline16 } from '../base/icons.js'
import styles from './Onboarding.module.css'

/** 两个模型的能力差异，用于引导页的对比卡片 */
const MODELS: {
  id: ChatModel
  name: string
  tagline: string
  points: string[]
  caution: string
}[] = [
  {
    id: 'deepseek-flash',
    name: 'DeepSeek Flash',
    tagline: '参数小 · 快 · 便宜',
    points: ['响应快，单价约为 Pro 的四分之一', '支持图片输入，能看图回答', '日常问答、查资料、写作够用'],
    caution: '推理深度有限，复杂情境下的对话质量一般',
  },
  {
    id: 'deepseek-v4-pro',
    name: 'DeepSeek V4 Pro',
    tagline: '参数量大 · 更懂语境',
    points: ['理解和演绎更强，长对话不容易跑偏', '角色扮演、情感陪伴明显更自然', '复杂任务与长文写作表现更好'],
    caution: '不支持图片输入，成本约为 Flash 的 4 倍',
  },
]

type Status = 'idle' | 'verifying' | 'invalid' | 'network' | 'error'

/**
 * 首次启动的引导页。
 *
 * 填 API Key → 校验（GET /models，不产生费用）→ 选模型 → 进入主界面。
 * 未完成前不渲染任何对话界面，避免用户在没配好 Key 的情况下点进聊天。
 */
export const Onboarding = () => {
  const savedKey = useSettings((state) => state.apiKey)
  const savedModel = useSettings((state) => state.model)
  const [key, setKey] = useState(savedKey ?? '')
  const [model, setModelLocal] = useState<ChatModel>(savedModel)
  const [reveal, setReveal] = useState(false)
  const [status, setStatus] = useState<Status>('idle')
  const [message, setMessage] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    // 移动端不自动聚焦：会立刻弹起键盘，遮住模型选择
    if (window.matchMedia('(min-width: 768px)').matches) {
      inputRef.current?.focus()
    }
  }, [])

  const trimmed = key.trim()
  const canSubmit = trimmed.length > 0 && status !== 'verifying'

  const submit = async () => {
    if (!canSubmit) {
      return
    }
    setStatus('verifying')
    setMessage('')
    try {
      await verifyApiKey(trimmed)
      setApiKey(trimmed)
      setModel(model)
      setOnboarded(true)
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 401 || error.status === 403) {
          setStatus('invalid')
          setMessage('这个 API Key 无效或没有权限，请检查后重试')
        } else if (error.kind === 'network') {
          setStatus('network')
          setMessage('连不上 api.deepseek.com，请检查网络后重试')
        } else {
          setStatus('error')
          setMessage(error.message || '校验失败，请稍后重试')
        }
      } else {
        setStatus('error')
        setMessage('校验失败，请稍后重试')
      }
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.brand}>
          {/* 品牌标记：与桌面图标一致的鲸鱼图；相对路径兼容 Capacitor 与浏览器直开 */}
          <img
            className={styles.logo}
            src="./brand-logo.png"
            alt=""
            aria-hidden="true"
            onError={(event) => {
              // 资源缺失时隐藏破图，品牌文字仍完整
              event.currentTarget.style.display = 'none'
            }}
          />
          <span className={styles.brandName}>DeepSeek</span>
          <span className={styles.brandBadge}>记忆版</span>
        </div>

        <h1 className={styles.title}>先接入你的 API Key</h1>
        <p className={styles.subtitle}>
          这个应用直接连 DeepSeek 官方接口，所有对话、图片和记忆都存在这台手机上。
        </p>

        <label className={styles.fieldLabel} htmlFor="onboarding-key">
          API Key
        </label>
        <div className={clsx(styles.inputWrap, status === 'invalid' && styles.inputError)}>
          {/*
            刻意不用 type="password"：在 Android WebView 上它会招来系统密码管理器，
            ColorOS 会弹填充提示盖住输入框、并接管点击，导致「点了没反应」。
            同时切换 type 会让 WebView 重建 input 节点，焦点与输入法组合态一起丢失，
            表现就是「点一下小眼睛，刚敲的字没了」。
            改为恒定 type="text" + CSS 文字遮蔽，切换时只改 class，节点不动。
          */}
          <input
            id="onboarding-key"
            ref={inputRef}
            name="deepseek-api-key"
            className={clsx(styles.input, !reveal && styles.masked)}
            type="text"
            value={key}
            placeholder="sk-..."
            autoComplete="off"
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            inputMode="text"
            enterKeyHint="go"
            onChange={(event) => {
              setKey(event.target.value)
              if (status !== 'idle' && status !== 'verifying') {
                setStatus('idle')
                setMessage('')
              }
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                void submit()
              }
            }}
          />
          <button
            type="button"
            className={styles.reveal}
            aria-label={reveal ? '隐藏 API Key' : '显示 API Key'}
            /*
             * 只拦 mousedown 阻止聚焦夺走焦点。
             * 千万不要拦 touchstart —— 它会连带吞掉后续的 click，
             * 表现就是「点小眼睛没反应」，粘贴后尤其明显。
             * 用 tabIndex=-1 让按钮本身不可聚焦，同样能保住输入框焦点。
             */
            tabIndex={-1}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => setReveal((value) => !value)}
          >
            {reveal ? <IconEyeOffOutline16 /> : <IconEyeOutline16 />}
          </button>
        </div>
        <p className={styles.hint}>
          在 platform.deepseek.com 的「API keys」页面创建。只保存在本机，不会发往除官方接口以外的任何地方。
        </p>

        <div className={styles.modelLabel}>选择模型</div>
        <div className={styles.models} role="radiogroup" aria-label="选择模型">
          {MODELS.map((item) => {
            const active = model === item.id
            return (
              <button
                key={item.id}
                type="button"
                role="radio"
                aria-checked={active}
                className={clsx(styles.model, active && styles.modelActive)}
                onClick={() => setModelLocal(item.id)}
              >
                <div className={styles.modelHead}>
                  <span className={styles.modelName}>{item.name}</span>
                  <span className={styles.modelDot} aria-hidden="true" />
                </div>
                <div className={styles.modelTagline}>{item.tagline}</div>
                <ul className={styles.modelPoints}>
                  {item.points.map((point) => (
                    <li key={point}>{point}</li>
                  ))}
                </ul>
                <div className={styles.modelCaution}>{item.caution}</div>
              </button>
            )
          })}
        </div>

        {message ? (
          <div className={styles.error} role="alert">
            {message}
          </div>
        ) : null}

        <Button
          block
          size="l"
          className={styles.submit}
          disabled={!canSubmit}
          onClick={() => void submit()}
        >
          {status === 'verifying' ? '正在校验…' : '确认并进入'}
        </Button>

        <p className={styles.footnote}>模型之后可以在设置里随时切换</p>
      </div>
    </div>
  )
}
