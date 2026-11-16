import { IconMemoryOutline16, IconMenuOutline20, IconNewChatOutline20 } from '../base/icons.js'
import { useTranslate } from '../../i18n/index.js'
import { newChat } from '../../services/chat/store.js'
import { openMemoryPanel } from '../../services/memory/store.js'
import { IconButton } from '../base/IconButton.js'
import { openMobileSider } from '../sider/mobileSiderState.js'
import styles from './MobileHeader.module.css'

/**
 * 移动端顶栏：左侧菜单打开侧边栏；会话页再显示标题、记忆入口与开启新对话。
 * 新对话页没有 sessionId，因此不显示记忆按钮——记忆以会话为单位，无会话则无处归属。
 */
export const MobileHeader = ({ title, sessionId }: { title?: string; sessionId?: string }) => {
  const t = useTranslate()
  return (
    <div className={styles.header}>
      <IconButton
        size="xl"
        tone="primary"
        icon={<IconMenuOutline20 />}
        aria-label={t('expandSiderbarButton')}
        onClick={openMobileSider}
      />
      {title === undefined ? null : (
        <>
          <div className={styles.title}>{title}</div>
          {sessionId ? (
            <IconButton
              size="xl"
              tone="primary"
              icon={<IconMemoryOutline16 />}
              aria-label={t('memoryButton')}
              onClick={() => void openMemoryPanel(sessionId)}
            />
          ) : null}
          <IconButton
            size="xl"
            tone="primary"
            icon={<IconNewChatOutline20 />}
            aria-label={t('newChat')}
            onClick={newChat}
          />
        </>
      )}
    </div>
  )
}
