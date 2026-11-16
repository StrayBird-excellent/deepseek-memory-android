package com.memoseek.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsDimen
import com.memoseek.mobile.ui.theme.DsMotion
import com.memoseek.mobile.ui.theme.DsTypography

/**
 * 输入框下方的功能胶囊（深度思考 / 智能搜索）。
 *
 * 交互对齐官方：
 * - 未选中：透明底 + 暗描边 + 次要文字色
 * - 选中：深蓝底 + 蓝描边 + 品牌蓝文字
 * - 按下时轻微内缩（0.96），回弹用同一条缓动曲线
 *
 * 只改 scale 与颜色，两者都在渲染层合成，不会引起重排，
 * 因此可以安全地和消息列表滚动同时运行。
 */
@Composable
fun CapsuleToggle(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(DsDimen.CapsuleCorner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = DsMotion.PressMillis),
        label = "capsuleScale",
    )

    val fill = if (selected) DsColors.CapsuleActiveFill else Color.Transparent
    val borderColor = if (selected) DsColors.CapsuleActiveBorder else DsColors.CapsuleBorder
    val contentColor = if (selected) DsColors.Brand else DsColors.TextSecondary

    Row(
        modifier = modifier
            .scale(scale)
            .height(DsDimen.CapsuleHeight)
            .clip(shape)
            .background(fill, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = contentColor.copy(alpha = 0.18f)),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = DsDimen.CapsuleHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = DsTypography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
