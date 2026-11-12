package com.timelock.app.domain.session

import com.timelock.app.data.history.UsageHistoryStore
import com.timelock.app.domain.model.SessionOutcome
import com.timelock.app.domain.model.UsageSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** 正在进行中的一次使用。 */
data class ActiveSession(
    val id: String,
    val packageName: String,
    val appLabel: String,
    /** 目的：选中的标签，或用户自己写的一句话。 */
    val purpose: String,
    val plannedMinutes: Int,
    val startedAt: Long,
    val extensions: Int = 0,
) {
    val totalMinutes: Int get() = plannedMinutes + extensions

    val plannedEndAt: Long get() = startedAt + totalMinutes * 60_000L

    fun remainingMillis(now: Long): Long = plannedEndAt - now

    fun usedMinutes(now: Long): Int =
        ((now - startedAt) / 60_000L).coerceAtLeast(0L).toInt()
}

/**
 * 会话状态的唯一归属者。
 *
 * 之所以把它做成单例放进容器，而不是塞在 Service 里：悬浮窗、通知、界面
 * 三方都要读同一份"现在到底在计时哪个应用"，只能有一个真相来源。
 */
class SessionManager(
    private val historyStore: UsageHistoryStore,
) {

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    suspend fun start(
        packageName: String,
        appLabel: String,
        purpose: String,
        plannedMinutes: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        val session = ActiveSession(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            appLabel = appLabel,
            purpose = purpose,
            plannedMinutes = plannedMinutes,
            startedAt = now,
        )
        _active.value = session
        historyStore.upsert(session.toRecord(outcome = SessionOutcome.IN_PROGRESS))
    }

    suspend fun extend(extraMinutes: Int, now: Long = System.currentTimeMillis()) {
        val current = _active.value ?: return
        val updated = current.copy(extensions = current.extensions + extraMinutes)
        _active.value = updated
        historyStore.upsert(updated.toRecord(outcome = SessionOutcome.IN_PROGRESS, now = now))
    }

    suspend fun finish(
        outcome: SessionOutcome = SessionOutcome.COMPLETED,
        now: Long = System.currentTimeMillis(),
    ) {
        val current = _active.value ?: return
        _active.value = null
        historyStore.upsert(current.toRecord(outcome = outcome, now = now))
    }

    /** 用户直接离开受管应用时调用：结束计时但标记为"提前结束"。 */
    suspend fun finishEarlyIfMatches(packageName: String, now: Long = System.currentTimeMillis()) {
        val current = _active.value ?: return
        if (current.packageName != packageName) return
        finish(outcome = SessionOutcome.FINISHED_EARLY, now = now)
    }
}

private fun ActiveSession.toRecord(
    outcome: SessionOutcome,
    now: Long = System.currentTimeMillis(),
): UsageSession = UsageSession(
    id = id,
    packageName = packageName,
    appLabel = appLabel,
    purpose = purpose,
    plannedMinutes = plannedMinutes,
    startedAt = startedAt,
    endedAt = if (outcome == SessionOutcome.IN_PROGRESS) null else now,
    actualMinutes = if (outcome == SessionOutcome.IN_PROGRESS) null else usedMinutes(now),
    outcome = outcome,
    extensions = extensions,
)
