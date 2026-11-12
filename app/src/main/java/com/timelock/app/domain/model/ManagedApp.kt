package com.timelock.app.domain.model

import kotlinx.serialization.Serializable

/**
 * 一个被纳入管理的应用。
 *
 * [dailyLimitMinutes] 为 null 表示"只做启动拦截、不设日额度"——
 * 每次打开仍需选择本次时长（这是本应用的核心机制）。
 */
@Serializable
data class ManagedApp(
    val packageName: String,
    val label: String,
    val dailyLimitMinutes: Int? = null,
    val enabled: Boolean = true,
    val addedAt: Long = 0L,
)
