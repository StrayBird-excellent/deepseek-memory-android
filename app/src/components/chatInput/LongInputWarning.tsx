import { useTranslate } from '../../i18n/index.js'
import { SERVER_INPUT_LIMIT } from '../../services/chat/preflight.js'
import { Button } from '../base/Button.js'
import { Modal } from '../base/Modal.js'
import styles from './LongInputWarning.module.css'

/** 发送前超长提示：让用户选择继续发送或返回拆分，不静默拦截 */
export const LongInputWarning = ({
  tokens,
  onCancel,
  onConfirm,
}: {
  tokens: number
  onCancel: () => void
  onConfirm: () => void
}) => {
  const t = useTranslate()
  return (
    <Modal
      title={t('longInputTitle')}
      closable
      onClose={onCancel}
      footer={
        <>
          <Button variant="outlined" onClick={onCancel}>
            {t('longInputBack')}
          </Button>
          <Button onClick={onConfirm}>{t('longInputSendAnyway')}</Button>
        </>
      }
    >
      <p className={styles.text}>
        {t('longInputBody', {
          tokens: tokens.toLocaleString(),
          limit: SERVER_INPUT_LIMIT.toLocaleString(),
        })}
      </p>
    </Modal>
  )
}
