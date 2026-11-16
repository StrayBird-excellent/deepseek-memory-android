package com.memoseek.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.i18n.DsText
import androidx.compose.ui.unit.sp
import com.memoseek.mobile.ui.components.ChatInputBar
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsDimen
import com.memoseek.mobile.ui.theme.DsTypography

/**
 * 首页。
 *
 * 结构：顶栏 → 居中问候语 → 底部输入区。
 * 输入区用 imePadding 跟随键盘上移，这是原生相对 WebView 的一个直接优势：
 * 键盘动画与界面位移同帧，不会出现点击错位。
 */
@Composable
fun HomeScreen(onStartChat: (String) -> Unit, onOpenDrawer: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var thinking by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        TopBar(onOpenDrawer = onOpenDrawer)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = DsText.s.greeting,
                style = DsTypography.headlineMedium,
                color = DsColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = DsDimen.ContentPadding),
            )
        }

        ChatInputBar(
            text = text,
            onTextChange = { text = it },
            thinking = thinking,
            onThinkingChange = { thinking = it },
            search = search,
            onSearchChange = { search = it },
            onSend = {
                if (text.isNotBlank()) {
                    onStartChat(text.trim())
                    text = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsDimen.InputMargin),
        )

        Spacer(Modifier.height(14.dp))
    }
}

/** 顶栏：仅一个汉堡按钮，官方首页也不显示标题。高度 48dp。 */
@Composable
private fun TopBar(onOpenDrawer: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DsDimen.TopBarHeight)
            .padding(start = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = DsText.s.openMenu,
                tint = DsColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
