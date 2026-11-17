import { useTranslate } from '../../i18n/index.js'
import clsx from 'clsx'
import { memo, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useIsMobile } from '../../app/viewport.js'
import { IconEditOutline16 } from '../base/icons.js'
import { editMessage, retryUpload, useChatStore } from '../../services/chat/store.js'
import { getVisibleMessages } from '../../services/chat/branches.js'
import { estimateRequestTokens, LONG_INPUT_WARN_TOKENS } from '../../services/chat/preflight.js'
import { useSettings } from '../../services/settings.js'
import {
  discardAttachment,
  isImage,
  resolveMimeType,
  uploadAttachment,
  useUploads,
  validateFiles,
} from '../../services/attachments.js'
import { createId } from '../../utils/id.js'
import { stopReadAloud } from '../../services/speech/readAloud.js'
import type { FileAttachment, UserMessage as UserMessageType } from '../../services/chat/types.js'
import { Button } from '../base/Button.js'
import { IconButton } from '../base/IconButton.js'
import { showToast } from '../base/toastState.js'
import { ensureApiKey } from '../settings/settingsDialogState.js'
import { AttachmentMenu } from '../chatInput/AttachmentMenu.js'
import { LongInputWarning } from '../chatInput/LongInputWarning.js'
import {
  BranchSwitcher,
  type MessageVersions,
  type SelectMessageVersion,
} from './BranchSwitcher.js'
import { MessageFile } from './MessageFile.js'
import { CopyButton } from './CopyButton.js'
import styles from './UserMessage.module.css'

/** 编辑时新增的附件只在内存里持有原件，发送成功后再统一写入 IndexedDB。 */
type EditFile = FileAttachment & {
  /** 本次编辑新增；旧附件为 false/undefined，不能删除远端文件 */
  added?: boolean
  blob?: Blob
  /** 新增图片的内存预览，原件落库前使用 */
  previewUrl?: string
}

export const UserMessage = memo(function UserMessage({
  sessionId,
  message,
  retryUploadId,
  isFirst,
  busy,
  versions,
  onSelectVersion,
}: {
  sessionId: string
  message: UserMessageType
  /** 回答因补传附件失败可重试时为该回答 id */
  retryUploadId: string | null
  isFirst: boolean
  busy: boolean
  versions: MessageVersions
  onSelectVersion: SelectMessageVersion
}) {
  const t = useTranslate()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(message.content)
  // 编辑时附件可增删；取消编辑直接丢弃新附件，不影响已发送的消息
  const [editFiles, setEditFiles] = useState<EditFile[]>(message.files)
  const [lengthWarning, setLengthWarning] = useState<number | null>(null)
  const pendingSendRef = useRef<(() => void) | null>(null)
  /** 提示弹层期间草稿仍可能被键盘修改，确认时执行最新一版的发送逻辑 */
  const sendEditRef = useRef<(force?: boolean) => void>(() => {})
  /** 本次编辑新增、尚未提交的附件 id：取消/移除时要清远端 */
  const newFilesRef = useRef(new Set<string>())
  /** 新增附件的内存预览地址；提交后仍保留到组件卸载，避免图片短暂空白 */
  const urlsRef = useRef(new Map<string, string>())
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const isMobile = useIsMobile()
  const uploadTasks = useUploads((state) => state.tasks)
  const files = editing ? editFiles : message.files
  const newFilesReady = editFiles.every(
    (file) => !file.added || uploadTasks[file.id]?.status === 'done'
  )
  const canSend = !busy && (draft.trim() !== '' || editFiles.length > 0) && newFilesReady

  useLayoutEffect(() => {
    const textarea = textareaRef.current
    if (editing && textarea) {
      textarea.style.height = 'auto'
      textarea.style.height = `${Math.min(textarea.scrollHeight, 202)}px`
    }
  }, [editing, draft])

  // 离开页面或切会话时，未提交的新附件按取消处理：中止上传并尽力删除远端文件。
  useEffect(
    () => () => {
      const apiKey = useSettings.getState().apiKey ?? null
      for (const id of newFilesRef.current) {
        discardAttachment(id, apiKey)
      }
      newFilesRef.current.clear()
      for (const url of urlsRef.current.values()) {
        URL.revokeObjectURL(url)
      }
      urlsRef.current.clear()
    },
    []
  )

  const releaseNewFile = (id: string) => {
    discardAttachment(id, useSettings.getState().apiKey ?? null)
    const url = urlsRef.current.get(id)
    if (url) {
      URL.revokeObjectURL(url)
      urlsRef.current.delete(id)
    }
    newFilesRef.current.delete(id)
  }

  const cancelEdit = () => {
    for (const id of [...newFilesRef.current]) {
      releaseNewFile(id)
    }
    setEditing(false)
  }

  const addEditFiles = (picked: File[]) => {
    if (picked.length === 0 || !ensureApiKey()) {
      return
    }
    if (
      useSettings.getState().model === 'deepseek-v4-pro' &&
      picked.some((file) => isImage(resolveMimeType(file)))
    ) {
      showToast({ key: 'modelImageUnsupported' })
      return
    }
    const error = validateFiles(picked, editFiles.length)
    if (error) {
      showToast(error)
      return
    }
    const apiKey = useSettings.getState().apiKey ?? ''
    const added = picked.map((file): EditFile => {
      const id = createId()
      const mimeType = resolveMimeType(file)
      const image = isImage(mimeType)
      const previewUrl = image ? URL.createObjectURL(file) : undefined
      if (previewUrl) {
        urlsRef.current.set(id, previewUrl)
      }
      newFilesRef.current.add(id)
      void uploadAttachment(apiKey, id, file, file.name, image).catch(() => {})
      return {
        id,
        serverId: null,
        name: file.name,
        size: file.size,
        isImage: image,
        mimeType,
        added: true,
        blob: file,
        previewUrl,
      }
    })
    setEditFiles((current) => [...current, ...added])
  }

  const retryEditFile = (file: EditFile) => {
    if (!file.blob || !ensureApiKey()) {
      return
    }
    void uploadAttachment(
      useSettings.getState().apiKey ?? '',
      file.id,
      file.blob,
      file.name,
      file.isImage
    ).catch(() => {})
  }

  const removeEditFile = (file: EditFile) => {
    if (file.added) {
      releaseNewFile(file.id)
    }
    setEditFiles((current) => current.filter((item) => item.id !== file.id))
  }

  /** 新附件必须全部上传成功；发送时把 serverId 和原件一起交给会话 */
  const prepareEditFiles = () => {
    const tasks = useUploads.getState().tasks
    const prepared: FileAttachment[] = []
    const newBlobs: { id: string; blob: Blob }[] = []
    for (const file of editFiles) {
      if (!file.added) {
        prepared.push({
          id: file.id,
          serverId: file.serverId,
          name: file.name,
          size: file.size,
          isImage: file.isImage,
          mimeType: file.mimeType,
        })
        continue
      }
      const task = tasks[file.id]
      if (task?.status !== 'done' || !file.blob) {
        return null
      }
      prepared.push({
        id: file.id,
        serverId: task.serverId,
        name: file.name,
        size: file.size,
        isImage: file.isImage,
        mimeType: file.mimeType,
      })
      newBlobs.push({ id: file.id, blob: file.blob })
    }
    return { files: prepared, newBlobs }
  }

  const closeLengthWarning = () => {
    pendingSendRef.current = null
    setLengthWarning(null)
  }

  const confirmLongSend = () => {
    const pending = pendingSendRef.current
    closeLengthWarning()
    pending?.()
  }

  const sendEdit = (force = false) => {
    if (!canSend) {
      return
    }
    const prepared = prepareEditFiles()
    if (!prepared || !ensureApiKey()) {
      return
    }
    if (
      useSettings.getState().model === 'deepseek-v4-pro' &&
      prepared.files.some((file) => file.isImage)
    ) {
      showToast({ key: 'modelImageUnsupported' })
      return
    }
    if (!force) {
      const session = useChatStore.getState().sessionCache[sessionId]
      const path = session ? getVisibleMessages(session) : []
      const index = path.findIndex((item) => item.id === message.id)
      const tokens = estimateRequestTokens({
        history: index >= 0 ? path.slice(0, index) : [],
        text: draft.trim(),
        files: prepared.files,
        includeMemory: true,
      })
      if (tokens >= LONG_INPUT_WARN_TOKENS) {
        textareaRef.current?.blur()
        pendingSendRef.current = () => sendEditRef.current(true)
        setLengthWarning(tokens)
        return
      }
    }
    if (!editMessage(sessionId, message.id, draft.trim(), prepared.files, prepared.newBlobs)) {
      return
    }
    stopReadAloud()
    // 已提交的附件不能再当作“新增未发送”清理；预览地址保留到卸载。
    newFilesRef.current.clear()
    setEditing(false)
  }
  sendEditRef.current = sendEdit

  return (
    <div className={clsx(styles.container, isFirst && styles.isFirst)}>
      {files.length > 0 ? (
        <div
          className={clsx(
            styles.files,
            files.some((file) => !file.isImage) && styles.filesWithDocuments
          )}
        >
          {files.map((file) => (
            <MessageFile
              key={file.id}
              file={file}
              previewUrl={urlsRef.current.get(file.id)}
              onRetry={
                (file as EditFile).added
                  ? () => retryEditFile(file as EditFile)
                  : retryUploadId
                    ? () => retryUpload(sessionId, retryUploadId)
                    : null
              }
              onRemove={editing ? () => removeEditFile(file as EditFile) : undefined}
            />
          ))}
        </div>
      ) : null}
      {editing ? (
        <div className={styles.editor}>
          <textarea
            ref={textareaRef}
            className={styles.textarea}
            aria-label={t('editMessage')}
            autoFocus
            rows={1}
            value={draft}
            disabled={busy}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.nativeEvent.isComposing) {
                return
              }
              if (event.key === 'Escape') {
                cancelEdit()
              } else if (event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !isMobile) {
                event.preventDefault()
                sendEdit()
              }
            }}
          />
          <div className={styles.editorActions}>
            <AttachmentMenu disabled={busy} onFiles={addEditFiles} />
            <div className={styles.editorButtons}>
              <Button size="s" variant="outlined" onClick={cancelEdit}>
                {t('cancel')}
              </Button>
              <Button size="s" disabled={!canSend} onClick={() => sendEdit()}>
                {t('send')}
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <>
          {message.content ? <div className={styles.bubble}>{message.content}</div> : null}
          <div className={styles.bottom}>
            <div className={styles.actions}>
              {message.content ? <CopyButton text={message.content} /> : null}
              <IconButton
                icon={<IconEditOutline16 />}
                tooltip={t('editMessage')}
                tooltipPlacement="bottom"
                disabled={busy}
                onClick={() => {
                  setDraft(message.content)
                  setEditFiles(message.files)
                  setEditing(true)
                }}
              />
            </div>
            <BranchSwitcher versions={versions} disabled={busy} onSelect={onSelectVersion} />
          </div>
        </>
      )}
      {lengthWarning !== null ? (
        <LongInputWarning
          tokens={lengthWarning}
          onCancel={closeLengthWarning}
          onConfirm={confirmLongSend}
        />
      ) : null}
    </div>
  )
})
