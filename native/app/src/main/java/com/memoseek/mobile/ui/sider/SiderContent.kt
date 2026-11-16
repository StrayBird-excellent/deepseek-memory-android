package com.memoseek.mobile.ui.sider

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.i18n.DsText
import androidx.compose.ui.unit.sp
import com.memoseek.mobile.data.model.Conversation
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsTypography

/**
 * 侧边栏。
 *
 * 结构与官方一致：顶部关闭按钮 + 搜索框，中间按时间分组的会话列表，底部账号区。
 * 分组口径见 [groupConversations]。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiderContent(
    conversations: List<Conversation>,
    activeId: String?,
    onSelect: (Conversation) -> Unit,
    onNewChat: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(conversations, query) {
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
    val groups = remember(filtered) { groupConversations(filtered) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(DsColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = DsText.s.close,
                    tint = DsColors.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        NewChatRow(onClick = onNewChat)

        if (groups.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isBlank()) DsText.s.noConversations else DsText.s.noMatchingConversations,
                    style = DsTypography.bodySmall,
                    color = DsColors.TextTertiary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 8.dp,
                ),
            ) {
                groups.forEach { group ->
                    stickyHeader(key = "header-${group.title}") {
                        Text(
                            text = group.title,
                            style = DsTypography.bodySmall.copy(fontSize = 12.sp),
                            color = DsColors.TextTertiary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DsColors.Background)
                                .padding(start = 10.dp, top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(group.conversations, key = { it.id }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            active = conversation.id == activeId,
                            onClick = { onSelect(conversation) },
                            onLongClick = { onDelete(conversation) },
                        )
                    }
                }
            }
        }

        AccountRow(onClick = onOpenSettings)
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp)),
        placeholder = {
            Text(
                text = DsText.s.searchConversations,
                style = DsTypography.bodySmall,
                color = DsColors.TextCaption,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = DsColors.TextTertiary,
                modifier = Modifier.size(16.dp),
            )
        },
        singleLine = true,
        textStyle = DsTypography.bodySmall.copy(color = DsColors.TextPrimary),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = DsColors.Surface,
            unfocusedContainerColor = DsColors.Surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun NewChatRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "+",
            style = DsTypography.bodyLarge,
            color = DsColors.TextPrimary,
        )
        Text(
            text = DsText.s.startNewChat,
            style = DsTypography.bodyLarge.copy(fontSize = 15.sp),
            color = DsColors.TextPrimary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) DsColors.Surface else Color.Transparent)
            // 官方没有常驻删除按钮，删除走长按
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = conversation.title,
            style = DsTypography.bodyLarge.copy(fontSize = 15.sp),
            color = DsColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 底部账号区。阶段 3 先把入口摆上，具体内容随设置页一起做。 */
@Composable
private fun AccountRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(DsColors.Brand),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "D",
                style = DsTypography.bodySmall,
                color = DsColors.Background,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "DeepSeek",
            style = DsTypography.bodyLarge.copy(fontSize = 15.sp),
            color = DsColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}