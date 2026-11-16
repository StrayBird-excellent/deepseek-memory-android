/*
 * 根构建脚本：只声明插件版本，不应用到根工程。
 *
 * AGP 8.13 + Kotlin 2.1.20：Kotlin 2.x 起 Compose 编译器随 Kotlin 一起发布，
 * 通过 org.jetbrains.kotlin.plugin.compose 启用，不再需要 composeOptions。
 */
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}
