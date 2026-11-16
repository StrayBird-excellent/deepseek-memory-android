package com.memoseek.mobile.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 主题模式。跟随系统时由 isSystemInDarkTheme 决定实际色板。 */
enum class ThemeMode { System, Light, Dark }

/**
 * 一套完整色板。
 *
 * 深色取值来自官方 App 像素采样；浅色官方未提供截图，
 * 按同一套语义层级推导（背景最浅、容器次之、文字最深），
 * 品牌蓝在浅底上加深以保证对比度。
 */
data class DsPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val bubble: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textCaption: Color,
    val brand: Color,
    val brandPressed: Color,
    val capsuleActiveFill: Color,
    val capsuleActiveBorder: Color,
    val capsuleBorder: Color,
    val error: Color,
)

private val DarkPalette = DsPalette(
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF242424),
    surfaceElevated = Color(0xFF1A1A1A),
    bubble = Color(0xFF333333),
    textPrimary = Color(0xFFE1E2EC),
    textSecondary = Color(0xFFB9BABB),
    textTertiary = Color(0xFF878787),
    textCaption = Color(0xFF7D7F85),
    brand = Color(0xFF6799FE),
    brandPressed = Color(0xFF7BA7FF),
    capsuleActiveFill = Color(0xFF283142),
    capsuleActiveBorder = Color(0xFF33436B),
    capsuleBorder = Color(0xFF2A2A2A),
    error = Color(0xFFF25A5A),
)

private val LightPalette = DsPalette(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F5F7),
    surfaceElevated = Color(0xFFEBECEF),
    bubble = Color(0xFFE8EAED),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF4D4F55),
    textTertiary = Color(0xFF8A8C92),
    textCaption = Color(0xFF9A9CA2),
    brand = Color(0xFF3B6EF5),
    brandPressed = Color(0xFF2F5FE0),
    capsuleActiveFill = Color(0xFFE8EFFF),
    capsuleActiveBorder = Color(0xFFC7D8FF),
    capsuleBorder = Color(0xFFE0E1E5),
    error = Color(0xFFD93A3A),
)

/**
 * 全局色板入口。
 *
 * 用全局可变状态而不是 CompositionLocal：色板被十多个组件引用，
 * 逐个改成 LocalDsPalette.current 侵入性大且容易漏改。
 * 主题切换是低频操作，重组成本可以忽略。
 *
 * 属性写成 getter 而不是常量，读取时才会订阅状态——
 * 这也是主题能实时切换的原因。
 */
object DsColors {
    private var palette by mutableStateOf(DarkPalette)

    val Background: Color get() = palette.background
    val Surface: Color get() = palette.surface
    val SurfaceElevated: Color get() = palette.surfaceElevated
    val Bubble: Color get() = palette.bubble
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary
    val TextTertiary: Color get() = palette.textTertiary
    val TextCaption: Color get() = palette.textCaption
    val Brand: Color get() = palette.brand
    val BrandPressed: Color get() = palette.brandPressed
    val CapsuleActiveFill: Color get() = palette.capsuleActiveFill
    val CapsuleActiveBorder: Color get() = palette.capsuleActiveBorder
    val CapsuleBorder: Color get() = palette.capsuleBorder
    val Error: Color get() = palette.error

    internal fun apply(light: Boolean) {
        palette = if (light) LightPalette else DarkPalette
    }
}
