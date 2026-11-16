package com.memoseek.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memoseek.mobile.data.repository.ChatRepository
import com.memoseek.mobile.data.repository.ConversationRepository
import com.memoseek.mobile.data.repository.MemoryRepository
import com.memoseek.mobile.data.repository.SettingsRepository
import com.memoseek.mobile.ui.chat.ChatScreen
import com.memoseek.mobile.ui.chat.ChatViewModel
import com.memoseek.mobile.ui.home.HomeScreen
import com.memoseek.mobile.ui.i18n.AppLanguage
import com.memoseek.mobile.ui.i18n.DsText
import com.memoseek.mobile.ui.settings.ApiKeyDialog
import com.memoseek.mobile.ui.memory.MemoryScreen
import com.memoseek.mobile.ui.settings.SettingsScreen
import com.memoseek.mobile.ui.sider.SiderContent
import com.memoseek.mobile.ui.theme.DeepSeekTheme
import com.memoseek.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 唯一的 Activity。
 *
 * 界面全部用 Compose 声明；enableEdgeToEdge 让内容铺满屏幕，
 * 安全区由各页面自行消费。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val settingsRepository = remember { SettingsRepository(context) }
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.System)
            val appLanguage by settingsRepository.language.collectAsState(initial = AppLanguage.System)
            DeepSeekTheme(themeMode = themeMode, language = appLanguage) {
                AppRoot(settingsRepository)
            }
        }
    }
}

/**
 * 应用骨架：抽屉 + 两屏切换。
 *
 * 只有「首页 / 聊天」两屏，用状态切换比引入 navigation-compose 更轻；
 * 抽屉用 Material3 的 ModalNavigationDrawer，滑动边缘手势、返回键关闭都是现成的。
 */
@Composable
private fun AppRoot(settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val chatRepository = remember { ChatRepository() }
    val conversationRepository = remember { ConversationRepository(context) }
    val memoryRepository = remember { MemoryRepository(context) }
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(chatRepository, settingsRepository, conversationRepository, memoryRepository),
    )

    val conversations by conversationRepository.conversations.collectAsState(initial = emptyList())
    val savedApiKey by settingsRepository.apiKey.collectAsState(initial = "")
    val currentThemeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.System)
    val currentModelId by settingsRepository.modelId.collectAsState(initial = com.memoseek.mobile.data.model.DefaultModel.id)
    val currentLanguage by settingsRepository.language.collectAsState(initial = AppLanguage.System)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var chatOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var memoryOpen by rememberSaveable { mutableStateOf(false) }
    val memories by memoryRepository.memories.collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<com.memoseek.mobile.data.model.Conversation?>(null) }
    var keyDialogOpen by rememberSaveable { mutableStateOf(false) }
    var currentKey by rememberSaveable { mutableStateOf("") }

    // 首次启动没有 Key 就直接把录入框摆出来，省掉一次找设置的动作
    LaunchedEffect(Unit) {
        currentKey = settingsRepository.apiKey.first()
        if (currentKey.isBlank()) keyDialogOpen = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 抽屉内容自己处理状态栏留白，避免和主内容重复 padding
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = com.memoseek.mobile.ui.theme.DsColors.Background,
            ) {
                SiderContent(
                    conversations = conversations,
                    activeId = null,
                    onSelect = { conversation ->
                        viewModel.openConversation(conversation)
                        chatOpen = true
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.newConversation()
                        chatOpen = false
                        scope.launch { drawerState.close() }
                    },
                    onClose = { scope.launch { drawerState.close() } },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        settingsOpen = true
                    },
                    onDelete = { conversation -> pendingDelete = conversation },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        },
    ) {
        if (memoryOpen) {
            MemoryScreen(
                memories = memories,
                onAdd = { scope.launch { memoryRepository.add(it) } },
                onDelete = { scope.launch { memoryRepository.remove(it) } },
                onBack = { memoryOpen = false },
            )
        } else if (settingsOpen) {
            SettingsScreen(
                apiKey = savedApiKey,
                themeMode = currentThemeMode,
                modelId = currentModelId,
                onBack = { settingsOpen = false },
                onEditApiKey = { keyDialogOpen = true },
                onThemeChange = { scope.launch { settingsRepository.setThemeMode(it) } },
                onModelChange = { scope.launch { settingsRepository.setModelId(it) } },
                onOpenMemory = { memoryOpen = true },
                memoryCount = memories.size,
                language = currentLanguage,
                onLanguageChange = { scope.launch { settingsRepository.setLanguage(it) } },
            )
        } else if (chatOpen) {
            ChatScreen(
                viewModel = viewModel,
                onBack = { chatOpen = false },
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )
        } else {
            HomeScreen(
                onStartChat = { text ->
                    viewModel.onInputChange(text)
                    viewModel.send()
                    chatOpen = true
                },
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )
        }
    }

    pendingDelete?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = com.memoseek.mobile.ui.theme.DsColors.Surface,
            title = {
                Text(
                    text = DsText.s.deleteConversationTitle,
                    style = com.memoseek.mobile.ui.theme.DsTypography.titleMedium,
                    color = com.memoseek.mobile.ui.theme.DsColors.TextPrimary,
                )
            },
            text = {
                Text(
                    text = String.format(DsText.s.deleteConversationBody, target.title),
                    style = com.memoseek.mobile.ui.theme.DsTypography.bodySmall,
                    color = com.memoseek.mobile.ui.theme.DsColors.TextSecondary,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch { conversationRepository.delete(target.id) }
                    if (chatOpen && target.id == viewModel.currentConversationId()) {
                        viewModel.newConversation()
                        chatOpen = false
                    }
                    pendingDelete = null
                }) {
                    Text(DsText.s.delete, color = com.memoseek.mobile.ui.theme.DsColors.Error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) {
                    Text(DsText.s.cancel, color = com.memoseek.mobile.ui.theme.DsColors.TextSecondary)
                }
            },
        )
    }

    if (keyDialogOpen) {
        ApiKeyDialog(
            initialValue = currentKey,
            onDismiss = { keyDialogOpen = false },
            onConfirm = { value ->
                currentKey = value
                keyDialogOpen = false
                scope.launch { settingsRepository.setApiKey(value) }
            },
        )
    }
}
