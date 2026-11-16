package com.memoseek.mobile.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 官方「深度思考」图标：两条交叉椭圆轨道 + 中心点。
 *
 * 官方这份图标没有落在 drawable 资源里（Compose 代码内联的 ImageVector，
 * 加固后从 APK 也取不出来），这里按官方截图的比例手绘：
 * 椭圆 rx/ry 约 4.8/2.4，两个轨道分别旋转 ±45°，中心实心点半径 1。
 *
 * 颜色由 Icon 的 tint 统一着色，这里填什么都无所谓。
 */
val AtomOutline: ImageVector by lazy {
    ImageVector.Builder(
        name = "AtomOutline",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
    ).apply {
        // 椭圆轨迹（横放），再整体旋转成两条交叉轨道
        fun orbit(degrees: Float) {
            group(rotate = degrees, pivotX = 8f, pivotY = 8f) {
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.15f,
                ) {
                    moveTo(3.2f, 8f)
                    arcTo(
                        horizontalEllipseRadius = 4.8f,
                        verticalEllipseRadius = 2.4f,
                        theta = 0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        x1 = 12.8f,
                        y1 = 8f,
                    )
                    arcTo(
                        horizontalEllipseRadius = 4.8f,
                        verticalEllipseRadius = 2.4f,
                        theta = 0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        x1 = 3.2f,
                        y1 = 8f,
                    )
                    close()
                }
            }
        }

        orbit(45f)
        orbit(-45f)

        // 中心实心点
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 6.9f)
            arcTo(1.1f, 1.1f, 0f, false, true, 8f, 9.1f)
            arcTo(1.1f, 1.1f, 0f, false, true, 8f, 6.9f)
            close()
        }
    }.build()
}
