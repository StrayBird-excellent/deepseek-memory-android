import { useCallback, useEffect, useState } from 'react'
import { Browser } from '@capacitor/browser'
import { useTranslate } from '../../i18n/index.js'
import { useSettings } from '../../services/settings.js'
import {
  fetchBalance,
  formatBalance,
  TOP_UP_URL,
  USAGE_URL,
  type Balance,
} from '../../services/api/balance.js'
import { Button } from '../base/Button.js'
import { IconRefreshOutline16 } from '../base/icons.js'
import styles from './AccountSetting.module.css'

/**
 * 账户页。
 *
 * 余额走官方 GET /user/balance，只需 API Key；
 * 充值打开官方页面，支付全程在 deepseek.com 完成，本应用不接触任何支付信息，
 * 也不做账号登录——这个应用的身份凭证就是 API Key。
 */
export const AccountSetting = () => {
  const t = useTranslate()
  const apiKey = useSettings((state) => state.apiKey)
  const [balance, setBalance] = useState<Balance | null>(null)
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    if (!apiKey) {
      setBalance(null)
      return
    }
    setLoading(true)
    setFailed(false)
    try {
      setBalance(await fetchBalance(apiKey))
    } catch {
      setFailed(true)
      setBalance(null)
    } finally {
      setLoading(false)
    }
  }, [apiKey])

  useEffect(() => {
    void load()
  }, [load])

  const openExternally = async (url: string) => {
    try {
      await Browser.open({ url })
    } catch {
      // 插件不可用（如浏览器预览）时退回普通跳转
      window.open(url, '_blank', 'noopener')
    }
  }

  const primary = balance?.balance_infos[0]

  return (
    <div className={styles.wrap}>
      <div className={styles.card}>
        <div className={styles.cardHead}>
          <span className={styles.cardLabel}>{t('accountBalance')}</span>
          <button
            type="button"
            className={styles.refresh}
            aria-label={t('accountRefresh')}
            disabled={loading || !apiKey}
            onClick={() => void load()}
          >
            <IconRefreshOutline16 />
          </button>
        </div>

        {!apiKey ? (
          <div className={styles.hint}>{t('accountNoKey')}</div>
        ) : failed ? (
          <div className={styles.error}>{t('accountLoadFailed')}</div>
        ) : (
          <>
            <div className={styles.amount}>{loading ? '…' : formatBalance(primary)}</div>
            <div className={styles.meta}>
              {balance ? (
                <>
                  <span>
                    {t('accountGranted')} {primary ? primary.granted_balance : '—'}
                  </span>
                  <span className={styles.dot} aria-hidden>
                    ·
                  </span>
                  <span>
                    {t('accountToppedUp')} {primary ? primary.topped_up_balance : '—'}
                  </span>
                  <span
                    className={balance.is_available ? styles.ok : styles.warn}
                  >
                    {balance.is_available ? t('accountAvailable') : t('accountUnavailable')}
                  </span>
                </>
              ) : (
                <span>{loading ? '' : t('accountLoadFailed')}</span>
              )}
            </div>
          </>
        )}
      </div>

      <div className={styles.actions}>
        <Button block size="l" onClick={() => void openExternally(TOP_UP_URL)}>
          {t('accountTopUp')}
        </Button>
        <Button
          block
          size="l"
          variant="outlined"
          onClick={() => void openExternally(USAGE_URL)}
        >
          {t('accountUsage')}
        </Button>
      </div>

      <p className={styles.note}>{t('accountTopUpHint')}</p>
    </div>
  )
}
