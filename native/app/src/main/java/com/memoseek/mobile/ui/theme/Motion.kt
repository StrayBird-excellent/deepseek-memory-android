package com.memoseek.mobile.ui.theme

/**
 * 动效时长 token。
 *
 * 官方是 Compose 的 Spring 动画，具体 stiffness 无法从 APK 中提取
 * （加固后连类名都不可读），这里用标准时长近似，
 * 统一收口后调手感只改这一处。
 */
object DsMotion {
    /** 按压反馈：必须足够快，否则显得迟钝 */
    const val PressMillis = 110

    /** 开关、颜色、透明度 */
    const val FastMillis = 160

    /** 面板展开收起 */
    const val NormalMillis = 240

    /** 抽屉、弹层 */
    const val SlowMillis = 320
}
