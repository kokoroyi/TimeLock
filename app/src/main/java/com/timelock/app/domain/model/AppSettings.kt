package com.timelock.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

/** 默认意图标签。「无聊，随手点开」与「习惯性打开」是这个功能的价值所在，不要删。 */
val DEFAULT_PURPOSE_TAGS: List<String> = listOf(
    "回消息",
    "查东西 / 办事",
    "工作或学习",
    "放松一下",
    "无聊，随手点开",
    "习惯性打开",
)

/**
 * 被判定为"反射性打开"的标签。
 *
 * 洞察页要算的就是这一类："这个月 63 次里，有 28 次是无聊随手点开的"——
 * 时长统计系统自带就有，只有时长+意图的组合才能刺痛人。
 */
val DEFAULT_REFLEXIVE_TAGS: List<String> = listOf(
    "无聊，随手点开",
    "习惯性打开",
)

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    /**
     * 「这次要做什么」永远是必填——拦截界面改成逐段解锁后，
     * 目的没填完时长那段根本不会出现，所以不再需要"是否强制填写"的开关。
     * 原先的 `requirePurpose` 字段已随 v2 删除。
     */
    val purposeTags: List<String> = DEFAULT_PURPOSE_TAGS,
    val reflexiveTags: List<String> = DEFAULT_REFLEXIVE_TAGS,
    val onboardingCompleted: Boolean = false,
    /** 到点前提前提醒的分钟数；0 表示不提醒。实际值还会被"会话时长÷3"压住，见服务里的说明。 */
    val remindBeforeMinutes: Int = 5,
    /** 白名单：这些包永不拦截。 */
    val whitelistPackages: Set<String> = emptySet(),
    /** 计算验证码：进入受管应用前先算一道题，答对才放行。**默认开启**。 */
    val mathChallengeEnabled: Boolean = true,
)
