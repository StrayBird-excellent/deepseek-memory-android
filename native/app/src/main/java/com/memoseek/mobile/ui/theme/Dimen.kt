package com.memoseek.mobile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸 token。
 *
 * 与色板同样来自官方实测：
 * - 顶栏 47.7dp
 * - 输入容器左右留白 11dp、圆角 20dp、总高约 97.7dp
 * - 功能胶囊高 29.3dp
 * 统一收口在这里，避免各处硬编码。
 */
object DsDimen {
    val TopBarHeight = 48.dp

    /** 正文左右留白：官方正文左缘距屏幕 22dp */
    val ContentPadding = 22.dp

    /** 输入容器与屏幕边缘的距离，比正文更窄 */
    val InputMargin = 11.dp
    val InputCorner = 20.dp
    val InputHorizontalPadding = 12.dp
    val InputTopPadding = 12.dp
    val InputBottomPadding = 13.dp

    val CapsuleHeight = 30.dp
    val CapsuleCorner = 15.dp
    val CapsuleHorizontalPadding = 11.dp
    val CapsuleGap = 10.dp

    val IconButtonSize = 34.dp
    val IconSize = 18.dp
}
