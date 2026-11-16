package com.memoseek.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.i18n.DsText
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsDimen
import com.memoseek.mobile.ui.theme.DsMotion
import com.memoseek.mobile.ui.theme.DsTypography

/**
 * 底部输入区。
 *
 * 结构对齐官方：整块是一个 #242424 的圆角容器，
 * 上半是输入行，下半是「两个功能胶囊 + 右侧两个圆形按钮」。
 * 右侧按钮会随输入状态切换：空输入显示语音，有文字显示发送。
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    thinking: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    search: Boolean,
    onSearchChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    val shape = RoundedCornerShape(DsDimen.InputCorner)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DsColors.Surface, shape)
            .padding(
                start = DsDimen.InputHorizontalPadding,
                end = DsDimen.InputHorizontalPadding,
                top = DsDimen.InputTopPadding,
                bottom = DsDimen.InputBottomPadding,
            ),
    ) {
        InputField(
            text = text,
            onTextChange = onTextChange,
            onSend = onSend,
        )

        Spacer(Modifier.size(0.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsDimen.CapsuleGap),
        ) {
            CapsuleToggle(
                label = DsText.s.deepThinking,
                icon = AtomOutline,
                selected = thinking,
                onClick = { onThinkingChange(!thinking) },
            )
            CapsuleToggle(
                label = DsText.s.smartSearch,
                icon = Icons.Outlined.Language,
                selected = search,
                onClick = { onSearchChange(!search) },
            )

            Spacer(Modifier.weight(1f))

            CircleAction(
                icon = Icons.Outlined.AttachFile,
                contentDescription = DsText.s.attachFile,
                onClick = { },
            )

            // 生成中优先显示停止；否则空输入是语音、有内容时是发送——和官方一致
            if (streaming) {
                CircleAction(
                    icon = Icons.Outlined.Stop,
                    contentDescription = DsText.s.stopGenerating,
                    onClick = onSend,
                )
            } else if (text.isBlank()) {
                CircleAction(
                    icon = Icons.Outlined.Mic,
                    contentDescription = DsText.s.voiceInput,
                    onClick = { },
                )
            } else {
                CircleAction(
                    icon = Icons.Outlined.Send,
                    contentDescription = DsText.s.send,
                    onClick = onSend,
                    tint = DsColors.Brand,
                )
            }
        }
    }
}

@Composable
private fun InputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 官方输入行实测 46dp（文字 24dp + 上下留白），这里用最小高度固定住
            .heightIn(min = 46.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) {
            Text(
                text = DsText.s.inputPlaceholder,
                style = DsTypography.bodyLarge,
                color = DsColors.TextCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = DsTypography.bodyLarge.copy(color = DsColors.TextPrimary),
            cursorBrush = SolidColor(DsColors.Brand),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = { if (text.isNotBlank()) onSend() },
            ),
            maxLines = 5,
        )
    }
}

/**
 * 右侧圆形按钮：附件、语音、发送共用。
 * 按下缩到 0.9，比胶囊更明显——它面积更小，需要更强的反馈才"手感对"。
 */
@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = DsColors.TextPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = DsMotion.PressMillis),
        label = "circleActionScale",
    )
    val animatedTint by animateColorAsState(
        targetValue = tint,
        animationSpec = tween(durationMillis = DsMotion.FastMillis),
        label = "circleActionTint",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(DsDimen.IconButtonSize)
            .scale(scale)
            .clip(CircleShape),
        interactionSource = interaction,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedTint,
            modifier = Modifier.size(DsDimen.IconSize),
        )
    }
}
