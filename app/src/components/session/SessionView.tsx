import clsx from 'clsx'
import { useIsMobile } from '../../app/viewport.js'
import { useTranslate } from '../../i18n/index.js'
import type { Session } from '../../services/chat/types.js'
import { openMemoryPanel } from '../../services/memory/store.js'
import { ChatInput } from '../chatInput/ChatInput.js'
import { IconButton } from '../base/IconButton.js'
import { IconMemoryOutline16 } from '../base/icons.js'
import { MessageList } from '../messages/MessageList.js'
import { MobileHeader } from './MobileHeader.js'
import styles from './SessionView.module.css'

/** 会话页：标题栏（移动端为顶栏）+ 消息列表，输入框吸底 */
export const SessionView = ({
  session,
  streaming,
  siderCollapsed,
}: {
  session: Session
  streaming: boolean
  siderCollapsed: boolean
}) => {
  const t = useTranslate()
  const isMobile = useIsMobile()
  const title = session.title || t('untitledChat')
  return (
    <div className={styles.view}>
      {isMobile ? (
        <MobileHeader title={title} sessionId={session.id} />
      ) : (
        <div className={styles.header}>
          <div className={clsx(styles.title, siderCollapsed && styles.withCollapsedSider)}>
            {title}
          </div>
          <IconButton
            className={styles.memoryButton}
            tone="primary"
            icon={<IconMemoryOutline16 />}
            aria-label={t('memoryButton')}
            onClick={() => void openMemoryPanel(session.id)}
          />
        </div>
      )}
      <MessageList
        session={session}
        streaming={streaming}
        footer={
          <div className={styles.inputContainer}>
            <ChatInput sessionId={session.id} />
          </div>
        }
      />
    </div>
  )
}
