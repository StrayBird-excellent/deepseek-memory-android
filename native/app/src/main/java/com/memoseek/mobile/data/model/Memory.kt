package com.memoseek.mobile.data.model

/**
 * 一条长期记忆。
 *
 * weight 是重要度（0~1），随引用次数上升、随时间衰减：
 * 越久没被用到越容易被淘汰，这就是艾宾浩斯遗忘曲线的工程近似。
 * 只保留内容与调度字段，不存原文——记忆是提炼过的结论，不是聊天记录。
 */
data class Memory(
    val id: String,
    val content: String,
    /** 重要度 0~1，低于阈值会被自动清理 */
    val weight: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    /** 引用次数，用于权重增长 */
    val useCount: Int = 0,
) {
    companion object {
        /** 低于这个权重视为可以遗忘 */
        const val FORGET_THRESHOLD = 0.15f

        /** 引用一次带来的权重增量 */
        const val USE_BOOST = 0.08f

        /** 每次衰减的周期：一天 */
        const val DECAY_PERIOD_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * 按经时间衰减后的实际权重。
     * 半衰期 7 天：一周不用，权重掉一半。
     */
    fun effectiveWeight(now: Long = System.currentTimeMillis()): Float {
        val days = (now - lastUsedAt).coerceAtLeast(0).toDouble() / DECAY_PERIOD_MS
        val decay = Math.pow(0.5, days / 7.0)
        return (weight * decay).toFloat()
    }

    fun used(now: Long = System.currentTimeMillis()): Memory =
        copy(lastUsedAt = now, useCount = useCount + 1, weight = (weight + USE_BOOST).coerceAtMost(1f))
}
