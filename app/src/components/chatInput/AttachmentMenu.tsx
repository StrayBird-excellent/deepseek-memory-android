import { useRef, useState } from 'react'
import {
  IconCameraOutline24,
  IconFileOutline24,
  IconImageOutline24,
  IconPlusOutline20,
} from '../base/icons.js'
import { IconButton } from '../base/IconButton.js'
import { Modal } from '../base/Modal.js'
import { useTranslate } from '../../i18n/index.js'
import { ACCEPT } from '../../services/attachments.js'
import styles from './AttachmentMenu.module.css'

/**
 * 添加附件：加号按钮 → 弹出「拍照 / 相册 / 文件」三栏。
 *
 * 设计要点（跨机型兼容）：
 * - 拍照走 capture="environment"，相册走 accept="image/*"，文件走扩展名白名单。
 *   三者分别是三个独立的 <input>，用完即清空 value，保证可以连续选同一个文件。
 * - 不直接申请 CAMERA / STORAGE 运行时权限：系统相机与文件选择器会自行处理授权，
 *   这样既避开 Android 13+ READ_MEDIA_IMAGES 的差异，也不会因为用户拒绝权限而彻底不可用。
 *   原生 Manifest 仍声明相机权限（required=false），供必须声明的 ROM 使用。
 * - 某些 WebView 不支持 capture，点击拍照会退回系统文件选择器，这是可接受的降级：
 *   用户仍能选到照片，只是不直接开相机。
 * - 每个入口的选择结果统一走 onFiles，由上层做格式/大小/数量校验。
 */
export const AttachmentMenu = ({
  onFiles,
  disabled,
}: {
  onFiles: (files: File[]) => void
  disabled?: boolean
}) => {
  const t = useTranslate()
  const [open, setOpen] = useState(false)
  const cameraRef = useRef<HTMLInputElement>(null)
  const albumRef = useRef<HTMLInputElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  /** 选择完成后关闭弹层并把文件交给上层；清空 value 以支持重复选择同一文件 */
  const pick = (input: HTMLInputElement | null) => {
    const files = Array.from(input?.files ?? [])
    if (input) {
      input.value = ''
    }
    setOpen(false)
    if (files.length > 0) {
      onFiles(files)
    }
  }

  /** 从菜单项触发对应 input；先关弹层再点击，避免部分 WebView 中弹层遮挡文件选择器 */
  const launch = (ref: React.RefObject<HTMLInputElement | null>) => {
    setOpen(false)
    // 用 rAF 让弹层先卸载，再唤起系统选择器，规避低端机上的点击穿透问题
    requestAnimationFrame(() => ref.current?.click())
  }

  return (
    <>
      <IconButton
        size="m"
        tone="primary"
        icon={<IconPlusOutline20 />}
        tooltip={t('attachmentAdd')}
        disabled={disabled}
        aria-expanded={open}
        onClick={() => setOpen(true)}
      />

      {open ? (
        <Modal
          title={t('attachmentTitle')}
          className={styles.sheet}
          closable
          onClose={() => setOpen(false)}
        >
          <div className={styles.grid}>
            <button type="button" className={styles.item} onClick={() => launch(cameraRef)}>
              <span className={styles.icon}>
                <IconCameraOutline24 />
              </span>
              <span className={styles.label}>{t('attachmentCamera')}</span>
            </button>
            <button type="button" className={styles.item} onClick={() => launch(albumRef)}>
              <span className={styles.icon}>
                <IconImageOutline24 />
              </span>
              <span className={styles.label}>{t('attachmentAlbum')}</span>
            </button>
            <button type="button" className={styles.item} onClick={() => launch(fileRef)}>
              <span className={styles.icon}>
                <IconFileOutline24 />
              </span>
              <span className={styles.label}>{t('attachmentFile')}</span>
            </button>
          </div>
          <p className={styles.hint}>{t('attachmentHint')}</p>
        </Modal>
      ) : null}

      {/* 拍照：系统相机；不支持 capture 的 WebView 会退化为文件选择器 */}
      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        hidden
        onChange={(event) => pick(event.currentTarget)}
      />
      {/* 相册：系统图库 / 文件选择器，只读图片 */}
      <input
        ref={albumRef}
        type="file"
        accept="image/*"
        multiple
        hidden
        onChange={(event) => pick(event.currentTarget)}
      />
      {/* 文件：走既有扩展名白名单，由上层校验格式与大小 */}
      <input
        ref={fileRef}
        type="file"
        accept={ACCEPT}
        multiple
        hidden
        onChange={(event) => pick(event.currentTarget)}
      />
    </>
  )
}
