import clsx from 'clsx'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useTranslate } from '../../i18n/index.js'
import { IconChevronRightOutline14, IconCloseOutline16 } from './icons.js'
import { IconButton } from './IconButton.js'
import styles from './Modal.module.css'

/** 当前打开的弹窗，后打开的在末尾；叠放时 Esc 只关闭最上层 */
const openModals: object[] = []

/** 遮罩 + 居中卡片，Esc / 点遮罩关闭 */
export const Modal = ({
  title,
  children,
  footer,
  closable = false,
  className,
  mainClassName,
  onClose,
  layout = 'dialog',
}: {
  title: ReactNode
  children?: ReactNode
  footer?: ReactNode
  /** 标题栏右侧显示关闭按钮 */
  closable?: boolean
  className?: string
  /** 内容区的 class */
  mainClassName?: string
  onClose: () => void
  /**
   * page：铺满全屏的页面形态（标题居中、左上角返回箭头），用于设置这类二级页面；
   * dialog：默认的居中弹窗。
   */
  layout?: 'dialog' | 'page'
}) => {
  const t = useTranslate()
  const titleId = useId()
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose
  const isPage = layout === 'page'
  useEffect(() => {
    const token = {}
    openModals.push(token)
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && openModals.at(-1) === token) {
        onCloseRef.current()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      openModals.splice(openModals.indexOf(token), 1)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [])

  return createPortal(
    <div
      className={clsx(styles.overlay, isPage && styles.pageOverlay)}
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <div
        className={clsx(styles.content, isPage && styles.pageContent, className)}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        {isPage ? (
          /* 页面形态：左上角返回箭头，标题居中，与官方二级页一致 */
          <div className={clsx(styles.header, styles.pageHeader)}>
            <IconButton
              className={styles.back}
              tone="primary"
              icon={<IconChevronRightOutline14 />}
              aria-label={t('back')}
              onClick={onClose}
            />
            <div id={titleId} className={styles.pageTitle}>
              {title}
            </div>
          </div>
        ) : (
          <div className={styles.header}>
            <div id={titleId} className={styles.title}>
              {title}
            </div>
            {closable ? (
              <IconButton
                className={styles.close}
                tone="primary"
                icon={<IconCloseOutline16 />}
                aria-label={t('close')}
                onClick={onClose}
              />
            ) : null}
          </div>
        )}
        {children ? (
          <div className={clsx(styles.main, isPage && styles.pageMain, mainClassName)}>
            {children}
          </div>
        ) : null}
        {footer ? <div className={styles.footer}>{footer}</div> : null}
      </div>
    </div>,
    document.body
  )
}
