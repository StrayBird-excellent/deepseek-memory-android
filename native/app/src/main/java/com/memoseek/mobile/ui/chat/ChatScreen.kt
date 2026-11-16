package com.memoseek.mobile.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.i18n.DsText
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.memoseek.mobile.data.model.Message
import com.memoseek.mobile.data.model.Role
import com.memoseek.mobile.ui.components.AtomOutline
import com.memoseek.mobile.ui.components.ChatInputBar
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsDimen
import com.memoseek.mobile.ui.theme.DsMotion
import com.memoseek.mobile.ui.theme.DsTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 聊天页。
 *
 * 布局与首页一致：顶栏 → 消息列表 → 输入区。
 * 列表用 LazyColumn 正向布局 + 自动滚动到底，而不是 reverseLayout：
 * 正向布局在长对话里插入历史更自然，滚动位置也更好控制。
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 离底部超过两条消息就显示「回到底部」，避免用户手动滑很久
    val farFromBottom by remember {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last < state.messages.lastIndex - 2
        }
    }

    // 新消息或增量到达时贴底；用户手动上滑后不强制拉回
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val nearBottom = lastVisible >= state.messages.lastIndex - 1
            if (nearBottom) {
                listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        ChatTopBar(
            title = state.title,
            canNewChat = state.messages.isNotEmpty(),
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            onNewChat = viewModel::newConversation,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                Text(
                    text = DsText.s.greeting,
                    style = DsTypography.headlineMedium,
                    color = DsColors.TextPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = DsDimen.ContentPadding,
                        end = DsDimen.ContentPadding,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        when (message.role) {
                            Role.User -> UserBubble(message)
                            Role.Assistant -> AssistantMessageItem(
                            message = message,
                            isLast = message.id == state.messages.lastOrNull { it.role == Role.Assistant }?.id,
                            onRetry = viewModel::retryLast,
                            onRegenerate = viewModel::regenerate,
                        )
                        }
                    }
                }

                if (farFromBottom) {
                    ScrollToBottom(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(state.messages.lastIndex)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }

        state.errorBanner?.let { banner ->
            ErrorBanner(text = banner, onDismiss = viewModel::dismissError)
        }

        ChatInputBar(
            text = state.input,
            onTextChange = viewModel::onInputChange,
            thinking = state.thinking,
            onThinkingChange = { viewModel.toggleThinking() },
            search = state.search,
            onSearchChange = { viewModel.toggleSearch() },
            onSend = { if (state.streaming) viewModel.stop() else viewModel.send() },
            streaming = state.streaming,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsDimen.InputMargin),
        )

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    canNewChat: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DsDimen.TopBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = DsText.s.openMenu,
                tint = DsColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = DsTypography.titleMedium,
            color = DsColors.TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            maxLines = 1,
        )
        if (canNewChat) {
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = DsText.s.newChat,
                    tint = DsColors.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** 用户气泡：右对齐、深灰底、20dp 圆角，内边距与官方一致（15 × 9） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(message: Message) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = message.content,
            style = DsTypography.bodyLarge,
            color = DsColors.TextPrimary,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DsColors.Bubble)
                // 官方用长按复制，不给气泡加常驻按钮，避免视觉噪音
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        android.widget.Toast
                            .makeText(context, DsText.s.copied, android.widget.Toast.LENGTH_SHORT)
                            .show()
                    },
                )
                .padding(horizontal = 15.dp, vertical = 9.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantMessageItem(message: Message, isLast: Boolean, onRetry: () -> Unit, onRegenerate: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.thinking.isNotEmpty()) {
            ThinkingBlock(
                thinking = message.thinking,
                streaming = message.streaming,
                startedAt = message.thinkingStartedAt,
                endedAt = message.thinkingEndedAt,
            )
            Spacer(Modifier.height(10.dp))
        }
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        val haptics = LocalHapticFeedback.current
        Box(
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    if (message.content.isNotBlank()) {
                        clipboard.setText(AnnotatedString(message.content))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        android.widget.Toast
                            .makeText(context, DsText.s.copied, android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                },
            ),
        ) {
            MarkdownText(text = message.content)
        }
        if (message.streaming) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "●",
                color = DsColors.Brand,
                fontSize = 10.sp,
                modifier = Modifier.pulsingDot(),
            )
        }
        if (isLast && !message.streaming && message.content.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = DsText.s.regenerate,
                style = DsTypography.bodySmall,
                color = DsColors.TextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRegenerate)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        message.error?.let { reason ->
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = reason, style = DsTypography.bodySmall, color = DsColors.Error)
                Text(
                    text = DsText.s.retry,
                    style = DsTypography.bodySmall,
                    color = DsColors.Brand,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * 思考块：默认折叠，只留一行标题，点开才看内容。
 * 官方行为一致——历史消息一律收起，避免正文被大段思考顶下去。
 */
@Composable
private fun ThinkingBlock(
    thinking: String,
    streaming: Boolean,
    startedAt: Long?,
    endedAt: Long?,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.animateContentSize(tween(DsMotion.NormalMillis))) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = AtomOutline,
                contentDescription = null,
                tint = DsColors.TextTertiary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = thinkingLabel(streaming, startedAt, endedAt),
                style = DsTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = DsColors.TextTertiary,
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = DsColors.TextTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = thinking,
                style = DsTypography.bodySmall.copy(lineHeight = 20.sp),
                color = DsColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = DsColors.Surface,
                        shape = RoundedCornerShape(2.dp, 10.dp, 10.dp, 2.dp),
                    )
                    .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DsDimen.InputMargin, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DsColors.Surface)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = DsTypography.bodySmall, color = DsColors.Error)
    }
}

/** 流式输出时的呼吸光标 */
@Composable
private fun Modifier.pulsingDot(): Modifier {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            visible = !visible
        }
    }
    return this.background(
        color = DsColors.Brand.copy(alpha = if (visible) 1f else 0.2f),
        shape = CircleShape,
    )
}

/**
 * 标题文案。
 *
 * 官方在思考结束后显示「已思考（用时 N 秒）」；不足 1 秒按 1 秒计，
 * 否则快速回答会显示「用时 0 秒」，观感很怪。
 */
private fun thinkingLabel(streaming: Boolean, startedAt: Long?, endedAt: Long?): String {
    if (streaming) return DsText.current.thinkingNow
    if (startedAt == null || endedAt == null) return DsText.current.thinkingDone
    val seconds = ((endedAt - startedAt).coerceAtLeast(0) / 1000).coerceAtLeast(1)
    return String.format(DsText.current.thinkingWithDuration, seconds)
}

/** 回到最新消息。浮在右下角，不挤占正文宽度。 */
@Composable
private fun ScrollToBottom(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DsColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = DsText.s.scrollToBottom,
            tint = DsColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
