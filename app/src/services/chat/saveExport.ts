import { Directory, Filesystem } from '@capacitor/filesystem'

/** 自研原生插件：系统「保存文件」对话框（SAF） */
type FileSaverPlugin = {
  save: (options: { name: string; data: string }) => Promise<{ uri: string; name: string }>
}

/** 是否运行在 Capacitor 原生壳里 */
const isNativeShell = (): boolean => {
  const cap = (window as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor
  return Boolean(cap?.isNativePlatform?.())
}

/** 取得自研插件实例；未注册时返回 null（用于降级） */
const getFileSaver = (): FileSaverPlugin | null => {
  const cap = (
    window as unknown as {
      Capacitor?: { Plugins?: { FileSaver?: FileSaverPlugin } }
    }
  ).Capacitor
  return cap?.Plugins?.FileSaver ?? null
}

/** Blob → base64（去掉 data URL 前缀）。原生端写文件只接受字符串或 base64。 */
const blobToBase64 = (blob: Blob): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error ?? new Error('读取导出文件失败'))
    reader.onload = () => {
      const result = reader.result
      if (typeof result !== 'string') {
        reject(new Error('读取导出文件失败'))
        return
      }
      // data:application/zip;base64,XXXX → XXXX
      resolve(result.slice(result.indexOf(',') + 1))
    }
    reader.readAsDataURL(blob)
  })

/**
 * 保存导出的 ZIP。
 *
 * 原生壳内走系统「保存文件」对话框（SAF）：
 * - WebView 不支持 `<a download>` 触发 blob 下载，原生环境下点了不会有任何反应；
 * - Capacitor Filesystem 的目录落在应用私有位置，用户在文件管理器里找不到，
 *   也无法再通过导入功能选回来。
 * 用系统对话框让用户自己选保存位置，文件既找得到，也能原样导回本应用。
 *
 * 浏览器环境回退到常规下载链接。
 *
 * @returns 保存后的文件名，用于界面提示
 */
export const saveExportedFile = async (name: string, blob: Blob): Promise<string> => {
  if (!isNativeShell()) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = name
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
    return name
  }

  const saver = getFileSaver()
  if (saver) {
    const base64 = await blobToBase64(blob)
    const result = await saver.save({ name, data: base64 })
    return result.name || name
  }

  // 插件未注册时的兜底：写入应用专属目录。文件位置较隐蔽，
  // 但至少不会像 WebView 下载那样完全无反应。
  const base64 = await blobToBase64(blob)
  await Filesystem.writeFile({
    path: name,
    data: base64,
    directory: Directory.External,
    recursive: true,
  })
  return name
}
