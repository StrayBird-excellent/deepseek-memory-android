import { getFileExt } from '../attachments.js'
import { estimateMessages, estimateTokens } from '../memory/tokenEstimate.js'
import { getContextRounds, getReplyText } from './reply.js'
import type { FileAttachment, Message } from './types.js'

/** 服务端当前单次请求的输入上限；超过会返回 400 user input too long */
export const SERVER_INPUT_LIMIT = 32_768
/** 留出估算误差和输出空间，到达这里就提示 */
export const LONG_INPUT_WARN_TOKENS = 28_000

/** 记忆注入和系统提示的固定预留 */
const MEMORY_RESERVE = 3_200
const REQUEST_OVERHEAD = 400
/** 文本按 UTF-8 约 3 字节/token 保守估算，中文接近 1 字/token */
const TEXT_BYTES_PER_TOKEN = 3

/** 这些格式的正文由服务端解析后才知道 token 数，文件大小不可靠，不计入估算 */
const UNESTIMATED_EXTS = new Set([
  'pdf',
  'doc',
  'docx',
  'ppt',
  'pptx',
  'xls',
  'xlsx',
  'epub',
  'mobi',
])

const fileTokens = (file: FileAttachment): number => {
  if (file.isImage || UNESTIMATED_EXTS.has(getFileExt(file.name))) {
    return 0
  }
  return Math.ceil(file.size / TEXT_BYTES_PER_TOKEN)
}

/**
 * 发送前的粗略输入估算：历史文本 + 本轮文字 + 文本类附件。
 * 图片和 PDF/Office 正文无法从文件大小推算，不纳入；估算只用于提示，不用于拦截。
 */
export const estimateRequestTokens = ({
  history,
  text,
  files,
  includeMemory,
}: {
  history: Message[]
  text: string
  files: FileAttachment[]
  includeMemory: boolean
}): number => {
  let tokens = REQUEST_OVERHEAD + estimateTokens(text)
  for (const [user, reply] of getContextRounds(history)) {
    tokens += estimateMessages([
      { role: 'user', text: user.content },
      { role: 'assistant', text: getReplyText(reply) },
    ])
    for (const file of user.files) {
      tokens += fileTokens(file)
    }
  }
  for (const file of files) {
    tokens += fileTokens(file)
  }
  if (includeMemory) {
    tokens += MEMORY_RESERVE
  }
  return tokens
}
