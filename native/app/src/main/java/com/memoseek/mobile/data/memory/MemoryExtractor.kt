package com.memoseek.mobile.data.memory

/**
 * 从用户消息里抽取可长期保留的事实。
 *
 * 刻意用规则而不是再调一次模型：
 * 抽取发生在每次发送的同步路径上，加一次网络往返会明显拖慢首字延迟；
 * 规则命中率不高，但**误抽的代价远大于漏抽**——漏了下次还会出现，
 * 抽错了会一直污染上下文。
 *
 * 因此这里的每个模式都要求明确的句式，且对提取值做严格过滤。
 */
object MemoryExtractor {

    private data class Rule(
        val regex: Regex,
        val template: (String) -> String,
    )

    /** 提取值里不能出现这些词，否则多半是把疑问句当陈述句了 */
    private val NOISE = listOf("你", "吗", "呢", "什么", "怎么", "为什么", "帮我", "一下")

    private val rules = listOf(
        Rule(Regex("""我(?:的名字)?叫([^\s，。！？、,.]{2,12})""")) { "用户叫「$it」" },
        Rule(Regex("""我的名字是([^\s，。！？、,.]{2,12})""")) { "用户叫「$it」" },
        Rule(Regex("""我在(?:做|开发|写|搞)([^\s，。！？、,.]{2,20})""")) { "用户在做「$it」" },
        Rule(Regex("""我的项目(?:是|叫)([^\s，。！？、,.]{2,20})""")) { "用户的项目是「$it」" },
        Rule(Regex("""我(?:喜欢|偏好|习惯用)([^\s，。！？、,.]{2,16})""")) { "用户偏好「$it」" },
        Rule(Regex("""我(?:平时|一般)?用([A-Za-z][A-Za-z0-9+#. ]{1,18})(?:开发|写代码|工作)""")) {
            "用户使用「$it」"
        },
        Rule(Regex("""我(?:是|在)([^\s，。！？、,.]{2,10})(?:工程师|开发者|程序员|设计师)""")) {
            "用户的职业是「$it」"
        },
    )

    /**
     * 返回可以写入记忆的陈述句。
     * 一条消息最多产出一条，避免长文本一次灌进一堆低质量记忆。
     */
    fun extract(message: String): List<String> {
        val text = message.trim()
        if (text.length < 4 || text.length > 200) return emptyList()

        for (rule in rules) {
            val match = rule.regex.find(text) ?: continue
            val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (!isMeaningful(value)) continue
            return listOf(rule.template(value))
        }
        return emptyList()
    }

    private fun isMeaningful(value: String): Boolean {
        if (value.length < 2) return false
        if (value.any { it.isDigit() && value.length <= 2 }) return false
        return NOISE.none { value.contains(it) }
    }
}
