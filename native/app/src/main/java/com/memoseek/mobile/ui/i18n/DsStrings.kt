package com.memoseek.mobile.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 界面语言。System 跟随系统 locale。 */
enum class AppLanguage { System, Chinese, English }

/**
 * 全部界面文案。
 *
 * 用数据类而不是 Android 的 strings.xml：这套界面全部由 Compose 渲染，
 * 走资源文件反而需要为了拿 Context 到处传参，且切换语言要重建 Activity。
 * 集中成一个对象后，换语言就是换一个实例，组合阶段自动重组。
 *
 * 带占位的文案用 %s / %d，由调用方 String.format。
 */
data class DsStrings(
    // 通用
    val back: String,
    val cancel: String,
    val save: String,
    val delete: String,
    val retry: String,
    val copied: String,
    val close: String,
    val openMenu: String,
    val selectModel: String,
    val selectLanguage: String,

    // 首页
    val greeting: String,
    val inputPlaceholder: String,
    val deepThinking: String,
    val smartSearch: String,

    // 聊天
    val newChat: String,
    val thinkingNow: String,
    val thinkingDone: String,
    val thinkingWithDuration: String,
    val regenerate: String,
    val stopGenerating: String,
    val send: String,
    val attachFile: String,
    val voiceInput: String,
    val scrollToBottom: String,

    // 侧边栏
    val startNewChat: String,
    val searchConversations: String,
    val noConversations: String,
    val noMatchingConversations: String,
    val deleteConversationTitle: String,
    val deleteConversationBody: String,

    // 分组
    val groupToday: String,
    val groupYesterday: String,
    val groupWeek: String,
    val groupMonth: String,
    /** 更早的按年月分组，参数是「年」和「月」 */
    val groupYearMonth: String,

    // 设置
    val settings: String,
    val groupAccount: String,
    val groupApp: String,
    val groupAbout: String,
    val apiKey: String,
    val apiKeyNotSet: String,
    val apiKeyHint: String,
    val apiKeyDialogHint: String,
    val balance: String,
    val loading: String,
    val balanceFailed: String,
    val appearance: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val language: String,
    val languageChinese: String,
    val languageEnglish: String,
    val languageSystem: String,
    val memory: String,
    val memoryDisabled: String,
    val memoryCountFormat: String,
    val version: String,
    val model: String,
    val paymentNote: String,

    // 记忆
    val addMemory: String,
    val addMemoryHint: String,
    val memoryEmpty: String,
    val memoryEmptyHint: String,
    val memoryInputPlaceholder: String,

    // 错误
    val errorNoApiKey: String,
    val errorInvalidKey: String,
    val errorInsufficientBalance: String,
    val errorRateLimited: String,
    val errorHttpFormat: String,
    val errorNetwork: String,
)

private val ChineseStrings = DsStrings(
    back = "返回",
    cancel = "取消",
    save = "保存",
    delete = "删除",
    retry = "重试",
    copied = "已复制",
    close = "关闭",
    openMenu = "打开菜单",
    selectModel = "模型",
    selectLanguage = "语言",

    greeting = "今天有什么可以帮到你？",
    inputPlaceholder = "发消息或按住说话",
    deepThinking = "深度思考",
    smartSearch = "智能搜索",

    newChat = "新对话",
    thinkingNow = "正在思考…",
    thinkingDone = "已思考",
    thinkingWithDuration = "已思考（用时 %d 秒）",
    regenerate = "重新生成",
    stopGenerating = "停止生成",
    send = "发送",
    attachFile = "添加附件",
    voiceInput = "语音输入",
    scrollToBottom = "回到底部",

    startNewChat = "开启新对话",
    searchConversations = "搜索对话内容",
    noConversations = "还没有对话",
    noMatchingConversations = "没有匹配的对话",
    deleteConversationTitle = "删除对话",
    deleteConversationBody = "「%s」将被永久删除。",

    groupToday = "今天",
    groupYesterday = "昨天",
    groupWeek = "7 天内",
    groupMonth = "30 天内",
    groupYearMonth = "%d年%d月",

    settings = "设置",
    groupAccount = "账户",
    groupApp = "应用",
    groupAbout = "关于",
    apiKey = "API Key",
    apiKeyNotSet = "未设置",
    apiKeyHint = "在 platform.deepseek.com 创建，只保存在本机。",
    apiKeyDialogHint = "在 platform.deepseek.com 创建，只保存在本机。",
    balance = "余额",
    loading = "读取中…",
    balanceFailed = "余额读取失败",
    appearance = "外观",
    themeSystem = "跟随系统",
    themeLight = "浅色",
    themeDark = "深色",
    language = "语言",
    languageChinese = "中文（简体）",
    languageEnglish = "English",
    languageSystem = "跟随系统",
    memory = "记忆",
    memoryDisabled = "未启用",
    memoryCountFormat = "%d 条",
    version = "版本",
    model = "模型",
    paymentNote = "余额与充值以 DeepSeek 官方为准，本应用不代收任何款项。",

    addMemory = "添加记忆",
    addMemoryHint = "写一条你希望它长期记住的信息。",
    memoryEmpty = "还没有记忆",
    memoryEmptyHint = "对话里出现的关键信息会自动沉淀到这里",
    memoryInputPlaceholder = "例如：我在做一个桌宠项目",

    errorNoApiKey = "请先在设置里填入 API Key",
    errorInvalidKey = "API Key 无效或已失效",
    errorInsufficientBalance = "账户余额不足",
    errorRateLimited = "请求过于频繁，请稍后再试",
    errorHttpFormat = "请求失败（HTTP %d）",
    errorNetwork = "网络异常",
)

private val EnglishStrings = DsStrings(
    back = "Back",
    cancel = "Cancel",
    save = "Save",
    delete = "Delete",
    retry = "Retry",
    copied = "Copied",
    close = "Close",
    openMenu = "Open menu",
    selectModel = "Model",
    selectLanguage = "Language",

    greeting = "What can I help you with today?",
    inputPlaceholder = "Message DeepSeek",
    deepThinking = "DeepThink",
    smartSearch = "Search",

    newChat = "New chat",
    thinkingNow = "Thinking…",
    thinkingDone = "Thought",
    thinkingWithDuration = "Thought for %d s",
    regenerate = "Regenerate",
    stopGenerating = "Stop",
    send = "Send",
    attachFile = "Attach file",
    voiceInput = "Voice input",
    scrollToBottom = "Scroll to bottom",

    startNewChat = "New chat",
    searchConversations = "Search chats",
    noConversations = "No chats yet",
    noMatchingConversations = "No matches",
    deleteConversationTitle = "Delete chat",
    deleteConversationBody = "\"%s\" will be permanently deleted.",

    groupToday = "Today",
    groupYesterday = "Yesterday",
    groupWeek = "Previous 7 days",
    groupMonth = "Previous 30 days",
    groupYearMonth = "%d-%02d",

    settings = "Settings",
    groupAccount = "Account",
    groupApp = "App",
    groupAbout = "About",
    apiKey = "API Key",
    apiKeyNotSet = "Not set",
    apiKeyHint = "Create one at platform.deepseek.com; stored on device only.",
    apiKeyDialogHint = "Create one at platform.deepseek.com; stored on device only.",
    balance = "Balance",
    loading = "Loading…",
    balanceFailed = "Failed to load balance",
    appearance = "Appearance",
    themeSystem = "System",
    themeLight = "Light",
    themeDark = "Dark",
    language = "Language",
    languageChinese = "中文（简体）",
    languageEnglish = "English",
    languageSystem = "System",
    memory = "Memory",
    memoryDisabled = "Off",
    memoryCountFormat = "%d items",
    version = "Version",
    model = "Model",
    paymentNote = "Balance and top-ups are handled by DeepSeek. This app never processes payments.",

    addMemory = "Add memory",
    addMemoryHint = "Write something you want it to remember.",
    memoryEmpty = "No memories yet",
    memoryEmptyHint = "Key facts from your chats will collect here",
    memoryInputPlaceholder = "e.g. I'm building a desktop pet",

    errorNoApiKey = "Add your API key in Settings first",
    errorInvalidKey = "Invalid or expired API key",
    errorInsufficientBalance = "Insufficient balance",
    errorRateLimited = "Too many requests, try again later",
    errorHttpFormat = "Request failed (HTTP %d)",
    errorNetwork = "Network error",
)

/**
 * 当前文案。
 *
 * 与色板同样的模式：全局可变状态 + getter，
 * 读取文案的组件在切换语言时自动重组，不需要重建 Activity。
 */
object DsText {
    private var strings by mutableStateOf(ChineseStrings)
    val s: DsStrings get() = strings

    internal fun apply(language: AppLanguage, systemIsEnglish: Boolean) {
        val useEnglish = when (language) {
            AppLanguage.English -> true
            AppLanguage.Chinese -> false
            AppLanguage.System -> systemIsEnglish
        }
        strings = if (useEnglish) EnglishStrings else ChineseStrings
    }

    /** 供侧边栏分组这类非 Composable 代码取用 */
    val current: DsStrings get() = strings
}
