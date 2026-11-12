package com.timelock.app.domain.model

import kotlinx.serialization.Serializable

/** 一次「打开受管应用 → 承诺时长 → 使用」的完整记录。 */
@Serializable
data class UsageSession(
    val id: String,
    val packageName: String,
    val appLabel: String,
    /**
     * 本次打开的目的。可以是选的标签，也可以是用户自己写的一句话
     * （v2 起「自定义」本身就是有效的目的，不再是"补充备注"）。
     * 空字符串只可能来自旧版本数据。
     */
    val purpose: String = "",
    /** 本次承诺的分钟数。 */
    val plannedMinutes: Int,
    val startedAt: Long,
    /** 结束时间；null 表示仍在进行中。 */
    val endedAt: Long? = null,
    /** 实际使用分钟数；结束时结算。 */
    val actualMinutes: Int? = null,
    val outcome: SessionOutcome = SessionOutcome.IN_PROGRESS,
    /** 加时次数（临时加时功能）。 */
    val extensions: Int = 0,
)

@Serializable
enum class SessionOutcome {
    /** 还在计时中。 */
    IN_PROGRESS,

    /** 用满了承诺时长。 */
    COMPLETED,

    /** 承诺时长内用户主动离开该应用。 */
    FINISHED_EARLY,

    /** 在锁屏弹窗上选择「下次一定」，直接放弃本次使用。 */
    SKIPPED,

    /** 发生过加时。 */
    EXTENDED,
}
