package com.memoseek.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.memoseek.mobile.ui.i18n.AppLanguage
import com.memoseek.mobile.ui.i18n.DsText
import java.util.Locale

/**
 * 主题。
 *
 * 深色是官方唯一的默认皮肤，浅色由同一套色板语义推导。
 * 色板切换通过 DsColors.apply 写入全局状态，读取色板的组件会自动重组。
 */
@Composable
fun DeepSeekTheme(
    themeMode: ThemeMode = ThemeMode.System,
    language: AppLanguage = AppLanguage.System,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    // 幂等写入：值不变时不会触发重组，因此可以放在组合阶段
    DsColors.apply(light = !dark)
    DsText.apply(language, systemIsEnglish = systemLanguageIsEnglish())

    val scheme = if (dark) {
        darkColorScheme(
            primary = DsColors.Brand,
            onPrimary = DsColors.Background,
            background = DsColors.Background,
            onBackground = DsColors.TextPrimary,
            surface = DsColors.Surface,
            onSurface = DsColors.TextPrimary,
            surfaceVariant = DsColors.SurfaceElevated,
            onSurfaceVariant = DsColors.TextSecondary,
            outline = DsColors.CapsuleBorder,
            error = DsColors.Error,
        )
    } else {
        lightColorScheme(
            primary = DsColors.Brand,
            onPrimary = DsColors.Background,
            background = DsColors.Background,
            onBackground = DsColors.TextPrimary,
            surface = DsColors.Surface,
            onSurface = DsColors.TextPrimary,
            surfaceVariant = DsColors.SurfaceElevated,
            onSurfaceVariant = DsColors.TextSecondary,
            outline = DsColors.CapsuleBorder,
            error = DsColors.Error,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            // 浅色底需要深色状态栏图标，否则白底白字看不见
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = DsTypography,
        content = content,
    )
}

/** 系统语言是否为英文；AppLanguage.System 时据此决定文案 */
@Composable
private fun systemLanguageIsEnglish(): Boolean {
    val locales = androidx.compose.ui.platform.LocalConfiguration.current.locales
    val primary = if (locales.isEmpty) Locale.getDefault() else locales[0]
    return primary.language.equals("en", ignoreCase = true)
}
