package com.timelock.app.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * 判断"此刻前台是哪个应用"。整个拦截能力的第一环。
 *
 * 这里踩过三个坑，都写下来免得再犯：
 *
 * 1. **只看最后一个 RESUMED 事件是不够的。** 用户按 Home 回桌面后只会产生 PAUSED，
 *    忽略它就会一直把上一个应用当成前台，于是锁屏反复弹。必须取 RESUMED 与 PAUSED
 *    中真正最新的那个。
 *
 * 2. **回看窗口不能太短。** 曾经用 60 秒窗口：用户在同一个应用里待够一分钟、
 *    期间没有任何切换事件时，那条 RESUMED 就滑出了窗口，`queryEvents` 返回空，
 *    于是被误判成"读不到前台"，计时被打断。所以读不到事件时必须**沿用上次判断**。
 *
 * 3. **回看窗口也不能太长。** 为了修第 2 条，曾经把窗口放宽到 10 分钟——
 *    结果在 HyperOS 上跑一阵子后 `queryEvents` 会**直接阻塞不返回**：
 *    500ms 轮询 × 每次扫 10 分钟事件表，把 MIUI 的 UsageStatsService 压垮了。
 *    症状极具迷惑性：服务还在、通知还在、权限都对，就是永远不再拦截。
 *
 * 现在的做法是**只问增量**：首次建立状态时回看 2 分钟，之后每次只查
 * "上次查询到现在"这 500ms 的窗口。又准又便宜。
 */
class ForegroundAppDetector(context: Context) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(UsageStatsManager::class.java)

    private var lastKnownPackage: String? = null

    /** 上次成功查询到的时刻；下次只问这之后的增量。 */
    private var lastQueryEnd: Long = 0L

    @Synchronized
    fun currentForegroundPackage(): String? {
        val usm = usageStatsManager ?: return lastKnownPackage
        val now = System.currentTimeMillis()

        // 首次（或上次查询失败）时回看一段，用来建立初始状态；
        // 之后只问增量——这是避免压垮系统服务的关键。
        val begin = if (lastQueryEnd == 0L) now - INITIAL_LOOKBACK_MS else lastQueryEnd

        val events = runCatching { usm.queryEvents(begin, now) }.getOrNull()
            ?: return lastKnownPackage

        // 只有查询成功才推进游标，否则会永久丢掉这段窗口里的事件
        lastQueryEnd = now

        val event = UsageEvents.Event()
        var latestTimestamp = 0L
        var latestPackage: String? = null
        var sawAnyRelevantEvent = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            val isResume = type == UsageEvents.Event.MOVE_TO_FOREGROUND
            val isPause = type == UsageEvents.Event.MOVE_TO_BACKGROUND
            if (!isResume && !isPause) continue

            sawAnyRelevantEvent = true
            if (event.timeStamp < latestTimestamp) continue

            latestTimestamp = event.timeStamp
            latestPackage = if (isResume) event.packageName else null
        }

        // 没有新事件不等于"没有前台应用"——只说明用户一直待在同一个地方。
        if (!sawAnyRelevantEvent) return lastKnownPackage

        lastKnownPackage = latestPackage
        return latestPackage
    }

    /**
     * 丢弃查询游标，下次重新回看一段来建立状态。
     * 供看门狗在判定"轮询卡死"后调用。保留 [lastKnownPackage]——它仍然是有效信息。
     */
    @Synchronized
    fun reset() {
        lastQueryEnd = 0L
    }

    private companion object {
        /** 仅用于建立初始状态；之后走增量，不再回看这么久。 */
        const val INITIAL_LOOKBACK_MS = 2 * 60_000L
    }
}
