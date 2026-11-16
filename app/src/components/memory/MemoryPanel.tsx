import { useEffect, useMemo, useRef, useState } from 'react'
import clsx from 'clsx'
import { useTranslate } from '../../i18n/index.js'
import { setModel, useSettings } from '../../services/settings.js'
import type { ChatModel } from '../../services/api/types.js'
import { daysUntilForgotten } from '../../services/memory/ebbinghaus.js'
import {
  addManualMemory,
  clearAllMemory,
  closeMemoryPanel,
  memoryViews,
  removeMemory,
  restoreMemory,
  toggleMemoryPin,
  updateMemoryConfig,
  updateMemoryText,
  useMemoryStore,
} from '../../services/memory/store.js'
import type { MemoryEntry, MemoryKind } from '../../services/memory/types.js'
import { Button } from '../base/Button.js'
import { IconButton } from '../base/IconButton.js'
import {
  IconCloseOutline16,
  IconEditOutline16,
  IconMemoryOutline16,
  IconTrashOutline16,
  IconWarningOutline16,
} from '../base/icons.js'
import styles from './MemoryPanel.module.css'

/** 记忆强度的刷新间隔：强度以天为单位衰减，一分钟更新一次足够，也不会带来渲染压力 */
const TICK_MS = 60_000

const KIND_LABEL: Record<MemoryKind, string> = {
  fact: '事实',
  preference: '偏好',
  persona: '设定',
  event: '事件',
  summary: '摘要',
}

/** 新增记忆时的可选类别，摘要由系统生成、不在此列 */
const ADDABLE: Exclude<MemoryKind, 'summary'>[] = ['fact', 'preference', 'persona', 'event']

const MODEL_OPTIONS: { id: ChatModel; label: string; hint: string }[] = [
  { id: 'deepseek-flash', label: 'Flash', hint: '快、省，支持图片' },
  { id: 'deepseek-v4-pro', label: 'V4 Pro', hint: '更懂语境，适合角色扮演' },
]

/** 强度色彩：清晰偏品牌蓝，模糊转琥珀，淡忘回落到灰 */
const strengthClass = (state: 'active' | 'fading' | 'forgotten') =>
  state === 'active' ? styles.barActive : state === 'fading' ? styles.barFading : styles.barGone

/**
 * 记忆面板。
 *
 * 移动端从底部升起，桌面端贴右侧。面板本身不参与对话逻辑，
 * 只读写记忆存储；所有变更立即落库，关闭再打开仍在。
 */
export const MemoryPanel = () => {
  const t = useTranslate()
  const { panelOpen, entries, config, loading, extracting, sessionId } = useMemoryStore()
  const model = useSettings((state) => state.model)
  const [now, setNow] = useState(() => Date.now())
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [adding, setAdding] = useState(false)
  const [newText, setNewText] = useState('')
  const [newKind, setNewKind] = useState<Exclude<MemoryKind, 'summary'>>('fact')
  const [confirmClear, setConfirmClear] = useState(false)
  const editRef = useRef<HTMLTextAreaElement>(null)

  // 面板打开时按固定节奏重算强度，让「正在淡忘」的过程肉眼可见
  useEffect(() => {
    if (!panelOpen) {
      return
    }
    setNow(Date.now())
    const timer = window.setInterval(() => setNow(Date.now()), TICK_MS)
    return () => window.clearInterval(timer)
  }, [panelOpen])

  // Esc 关闭；编辑态下先退出编辑
  useEffect(() => {
    if (!panelOpen) {
      return
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') {
        return
      }
      if (editingId) {
        setEditingId(null)
        return
      }
      closeMemoryPanel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [panelOpen, editingId])

  useEffect(() => {
    if (editingId) {
      editRef.current?.focus()
      editRef.current?.select()
    }
  }, [editingId])

  const views = useMemo(() => memoryViews(entries, now), [entries, now])
  const stats = useMemo(() => {
    const live = views.filter((view) => view.state !== 'forgotten')
    const avg =
      live.length === 0 ? 0 : live.reduce((sum, view) => sum + view.retention, 0) / live.length
    return {
      live: live.length,
      fading: views.filter((view) => view.state === 'fading').length,
      forgotten: views.filter((view) => view.state === 'forgotten').length,
      avg,
    }
  }, [views])

  if (!panelOpen) {
    return null
  }

  const startEdit = (entry: MemoryEntry) => {
    setEditingId(entry.id)
    setDraft(entry.text)
  }

  const commitEdit = async () => {
    if (editingId && draft.trim()) {
      await updateMemoryText(editingId, draft)
    }
    setEditingId(null)
  }

  const commitAdd = async () => {
    if (!newText.trim()) {
      return
    }
    await addManualMemory(newText, newKind)
    setNewText('')
    setAdding(false)
  }

  return (
    <>
      <div className={styles.scrim} onMouseDown={closeMemoryPanel} />
      <aside className={styles.panel} role="dialog" aria-label={t('memoryTitle')} aria-modal="true">
        <header className={styles.header}>
          <div className={styles.headerMain}>
            <IconMemoryOutline16 />
            <span className={styles.title}>{t('memoryTitle')}</span>
            <span className={styles.count}>{stats.live}</span>
          </div>
          <IconButton
            tone="primary"
            icon={<IconCloseOutline16 />}
            aria-label={t('close')}
            onClick={closeMemoryPanel}
          />
        </header>

        {/* 概览：平均清晰度 + 分布。数字会随时间自己变化，这是遗忘曲线在起作用 */}
        <div className={styles.overview}>
          <div className={styles.overviewRow}>
            <span className={styles.overviewLabel}>{t('memoryAvgRetention')}</span>
            <span className={styles.overviewValue}>{Math.round(stats.avg * 100)}%</span>
          </div>
          <div className={styles.overviewBar}>
            <div
              className={clsx(styles.overviewFill, stats.avg < 0.5 && styles.overviewFillLow)}
              style={{ width: `${Math.max(2, Math.round(stats.avg * 100))}%` }}
            />
          </div>
          <div className={styles.overviewMeta}>
            <span>{t('memoryLiveCount', { count: stats.live })}</span>
            {stats.fading > 0 ? (
              <span className={styles.metaFading}>
                {t('memoryFadingCount', { count: stats.fading })}
              </span>
            ) : null}
            {stats.forgotten > 0 ? (
              <span className={styles.metaGone}>
                {t('memoryGoneCount', { count: stats.forgotten })}
              </span>
            ) : null}
          </div>
        </div>

        {/* 模型切换：角色扮演场景常用，放在记忆面板里随手可改 */}
        <div className={styles.modelRow}>
          <span className={styles.rowLabel}>{t('memoryModelLabel')}</span>
          <div className={styles.segment}>
            {MODEL_OPTIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                title={option.hint}
                className={clsx(styles.segmentItem, model === option.id && styles.segmentActive)}
                onClick={() => setModel(option.id)}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        <div className={styles.list}>
          {loading ? (
            <div className={styles.placeholder}>{t('memoryLoading')}</div>
          ) : views.length === 0 ? (
            <div className={styles.empty}>
              <div className={styles.emptyTitle}>{t('memoryEmpty')}</div>
              <div className={styles.emptyHint}>{t('memoryEmptyHint')}</div>
            </div>
          ) : (
            views.map(({ entry, retention: r, state }) => (
              <div
                key={entry.id}
                className={clsx(
                  styles.item,
                  state === 'fading' && styles.itemFading,
                  state === 'forgotten' && styles.itemGone
                )}
              >
                <div className={styles.itemHead}>
                  <span className={styles.kind}>{KIND_LABEL[entry.kind]}</span>
                  {entry.pinned ? <span className={styles.pinFlag}>{t('memoryPinned')}</span> : null}
                  {entry.manual ? <span className={styles.manualFlag}>{t('memoryManual')}</span> : null}
                  <span className={styles.itemActions}>
                    <button
                      type="button"
                      className={styles.action}
                      title={entry.pinned ? t('memoryUnpin') : t('memoryPin')}
                      onClick={() => void toggleMemoryPin(entry.id)}
                    >
                      {entry.pinned ? '★' : '☆'}
                    </button>
                    <button
                      type="button"
                      className={styles.action}
                      title={t('memoryEdit')}
                      onClick={() => startEdit(entry)}
                    >
                      <IconEditOutline16 />
                    </button>
                    <button
                      type="button"
                      className={clsx(styles.action, styles.actionDanger)}
                      title={t('memoryDelete')}
                      onClick={() => void removeMemory(entry.id)}
                    >
                      <IconTrashOutline16 />
                    </button>
                  </span>
                </div>

                {editingId === entry.id ? (
                  <div className={styles.editWrap}>
                    <textarea
                      ref={editRef}
                      className={styles.editInput}
                      value={draft}
                      rows={2}
                      onChange={(event) => setDraft(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' && !event.shiftKey) {
                          event.preventDefault()
                          void commitEdit()
                        }
                      }}
                    />
                    <div className={styles.editActions}>
                      <Button size="s" variant="outlined" onClick={() => setEditingId(null)}>
                        {t('cancel')}
                      </Button>
                      <Button size="s" onClick={() => void commitEdit()}>
                        {t('confirm')}
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className={styles.itemText}>{entry.text}</div>
                )}

                <div className={styles.itemFoot}>
                  <div className={styles.barTrack}>
                    <div
                      className={clsx(styles.bar, strengthClass(state))}
                      style={{ width: `${entry.pinned ? 100 : Math.max(2, Math.round(r * 100))}%` }}
                    />
                  </div>
                  <span className={styles.barValue}>
                    {entry.pinned ? t('memoryPinnedForever') : `${Math.round(r * 100)}%`}
                  </span>
                  {state === 'forgotten' ? (
                    <button
                      type="button"
                      className={styles.restore}
                      onClick={() => void restoreMemory(entry.id)}
                    >
                      {t('memoryRestore')}
                    </button>
                  ) : entry.pinned ? null : (
                    <span className={styles.barDays}>
                      {t('memoryDaysLeft', { days: daysUntilForgotten(entry, now) })}
                    </span>
                  )}
                </div>
              </div>
            ))
          )}
        </div>

        <footer className={styles.footer}>
          {adding ? (
            <div className={styles.addBox}>
              <div className={styles.kindPicker}>
                {ADDABLE.map((kind) => (
                  <button
                    key={kind}
                    type="button"
                    className={clsx(styles.kindChip, newKind === kind && styles.kindChipActive)}
                    onClick={() => setNewKind(kind)}
                  >
                    {KIND_LABEL[kind]}
                  </button>
                ))}
              </div>
              <textarea
                className={styles.addInput}
                rows={2}
                autoFocus
                placeholder={t('memoryAddPlaceholder')}
                value={newText}
                onChange={(event) => setNewText(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault()
                    void commitAdd()
                  }
                }}
              />
              <div className={styles.addActions}>
                <Button size="s" variant="outlined" onClick={() => setAdding(false)}>
                  {t('cancel')}
                </Button>
                <Button size="s" onClick={() => void commitAdd()}>
                  {t('memoryAddConfirm')}
                </Button>
              </div>
            </div>
          ) : (
            <div className={styles.footerRow}>
              <Button size="s" variant="outlined" block onClick={() => setAdding(true)}>
                {t('memoryAdd')}
              </Button>
              <IconButton
                tone="primary"
                icon={<IconTrashOutline16 />}
                aria-label={t('memoryClear')}
                onClick={() => setConfirmClear(true)}
              />
            </div>
          )}

          <div className={styles.toggleRow}>
            <div className={styles.toggleText}>
              <span className={styles.toggleTitle}>{t('memoryAutoExtract')}</span>
              <span className={styles.toggleHint}>{t('memoryAutoExtractHint')}</span>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={config.autoExtract}
              className={clsx(styles.switch, config.autoExtract && styles.switchOn)}
              onClick={() => void updateMemoryConfig({ autoExtract: !config.autoExtract })}
            >
              <span className={styles.switchKnob} />
            </button>
          </div>

          {sessionId && extracting[sessionId] ? (
            <div className={styles.extracting}>{t('memoryExtracting')}</div>
          ) : null}
        </footer>
      </aside>

      {confirmClear ? (
        <div className={styles.confirmOverlay} onMouseDown={() => setConfirmClear(false)}>
          <div className={styles.confirm} onMouseDown={(event) => event.stopPropagation()}>
            <div className={styles.confirmHead}>
              <IconWarningOutline16 />
              <span>{t('memoryClear')}</span>
            </div>
            <p className={styles.confirmText}>{t('memoryClearConfirm')}</p>
            <div className={styles.confirmActions}>
              <Button size="s" variant="outlined" onClick={() => setConfirmClear(false)}>
                {t('cancel')}
              </Button>
              <Button
                size="s"
                variant="danger"
                onClick={() => {
                  void clearAllMemory()
                  setConfirmClear(false)
                }}
              >
                {t('memoryClearAction')}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
