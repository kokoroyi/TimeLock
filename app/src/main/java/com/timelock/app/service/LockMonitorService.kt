package com.timelock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.timelock.app.BuildConfig
import com.timelock.app.MainActivity
import com.timelock.app.R
import com.timelock.app.TimeLockApplication
import com.timelock.app.di.AppContainer
import com.timelock.app.domain.model.AppSettings
import com.timelock.app.domain.model.ManagedApp
import com.timelock.app.domain.model.SessionOutcome
import com.timelock.app.domain.session.ActiveSession
import com.timelock.app.ui.lock.BlockedScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务：监测前台应用，并在需要时弹出拦截界面。
 *
 * 这是整个应用"锁得住"与否的正面对决处。几个刻意的取舍：
 *
 * 1. **轮询用 500ms**：`UsageStatsManager` 没有回调接口，只能轮询。
 *    500ms 是"够快"与"够省电"之间的实测折中。
 * 2. **拦截界面走悬浮窗**，不用 Activity——见 [LockOverlayController] 的说明。
 * 3. **「这次算了」和「结束」都把人送回桌面**，而不是仅仅关掉浮层。
 *    否则用户只要点一下就能继续待在受管应用里，整个机制形同虚设。
 */
class LockMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private lateinit var overlay: LockOverlayController

    private var settings: AppSettings = AppSettings()
    private var managedApps: List<ManagedApp> = emptyList()

    private var shownTarget: OverlayTarget? = null
    private var lastNotifiedKey: String? = null
    private var pollJob: Job? = null
    private var watchdogJob: Job? = null
    private var nullStreak = 0

    /** 上一次轮询完整跑完的时刻，供看门狗判断"是不是卡死了"。 */
    private var lastTickCompletedAt = 0L

    /** 已经为哪个会话发过"到点前提醒"，保证每个会话只提醒一次。 */
    private var reminderSentForSessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as TimeLockApplication).container
        overlay = LockOverlayController(this)

        createNotificationChannel()
        promoteToForeground(buildNotification(null))

        scope.launch { container.settingsStore.settings.collect { settings = it } }
        scope.launch { container.settingsStore.managedApps.collect { managedApps = it } }
        lastTickCompletedAt = System.currentTimeMillis()
        pollJob = scope.launch { pollLoop() }
        watchdogJob = scope.launch { watchdogLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        pollJob?.cancel()
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MS)
            runCatching { tick() }
            lastTickCompletedAt = System.currentTimeMillis()
        }
    }

    /**
     * 轮询看门狗。
     *
     * `UsageStatsManager.queryEvents` 是跨进程调用，实测在 HyperOS 上会**卡住不再返回**。
     * 那时服务还好端端地挂着、通知也在、权限也对，就是永远不再拦截——
     * 用户完全察觉不到，这是最难查也最伤人的一种失败。
     *
     * 看门狗检测到轮询长时间没有跑完，就把它重启一遍（顺带重置探测器游标）。
     * 卡住的那次调用本身无法中断，另起一个协程即可绕开它。
     */
    private suspend fun watchdogLoop() {
        while (currentCoroutineContext().isActive) {
            delay(WATCHDOG_INTERVAL_MS)
            val silentFor = System.currentTimeMillis() - lastTickCompletedAt
            if (silentFor > WATCHDOG_TIMEOUT_MS) {
                Log.w(TAG, "轮询停摆 ${silentFor}ms，正在重启轮询")
                pollJob?.cancel()
                container.foregroundAppDetector.reset()
                lastTickCompletedAt = System.currentTimeMillis()
                pollJob = scope.launch { pollLoop() }
            }
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()

        // 先确认自己还有能力工作。
        // 国产 ROM（实测 HyperOS）会在用户毫不知情时回收悬浮窗权限，
        // 此时服务若不吭声地停掉，用户看到的就是最糟糕的那种"锁不住"——
        // 以为还在管着，其实早就不管了。所以这里必须**出声**。
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限已被回收，看护无法继续")
            notifyProtectionLost()
            stopSelf()
            return
        }

        val foreground = container.foregroundAppDetector.currentForegroundPackage()
        val active = container.sessionManager.active.value

        // 日志必须在所有提前 return 之前——否则"读不到前台"的轮次全部不留痕迹，
        // 排查时只能看到零星几条，误导性强（这个坑踩过）。
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "tick fg=$foreground managedApps=${managedApps.size} " +
                    "whitelist=${settings.whitelistPackages.size} active=${active?.packageName} " +
                    "nullStreak=$nullStreak shown=$shownTarget",
            )
        }

        // 瞬时读不到前台是常态：息屏、窗口切换、输入法弹出都会造成。
        // 只凭一次 null 就判定"用户离开了应用"，会让锁屏莫名其妙地消失。
        if (foreground == null) {
            nullStreak++
            if (nullStreak < NULL_STREAK_TOLERANCE) return
        } else {
            nullStreak = 0
        }

        // 前台是我们自己：不计时
        if (foreground == null || foreground == packageName) {
            endActiveSessionIfAny(active, now)
            dismissOverlay("前台为自己或持续读不到: $foreground")
            return
        }

        val managed = managedApps.firstOrNull {
            it.packageName == foreground &&
                it.enabled &&
                foreground !in settings.whitelistPackages
        }

        if (managed == null) {
            // 用户已经离开受管应用，本次使用到此为止
            endActiveSessionIfAny(active, now)
            dismissOverlay("前台已不是受管应用: $foreground")
            return
        }

        if (active == null || active.packageName != foreground) {
            // 已经处于"已挡住"状态且还是同一个应用：保持不动，不要重新弹问询
            val blocked = shownTarget as? OverlayTarget.Blocked
            if (blocked?.packageName == foreground) return

            // 从另一个受管应用切过来：先把上一段的计时结掉，
            // 否则用户在 B 里待的时间会被算进 A 的额度（时长虚高）。
            if (active != null) {
                container.sessionManager.finish(SessionOutcome.FINISHED_EARLY, now)
            }
            // 没有授权就进来了 —— 拦住
            showIfChanged(OverlayTarget.Prompt(foreground, container.appCatalog.labelOf(foreground)))
            return
        }

        if (active.remainingMillis(now) <= 0) {
            showIfChanged(OverlayTarget.TimeUp(active))
        } else {
            dismissOverlay("计时中，剩余 ${active.remainingMillis(now)} ms")
            updateNotification(active)
            maybeSendPreReminder(active, now)
        }
    }

    private suspend fun endActiveSessionIfAny(
        active: com.timelock.app.domain.session.ActiveSession?,
        now: Long,
    ) {
        if (active != null) {
            container.sessionManager.finish(SessionOutcome.FINISHED_EARLY, now)
        }
    }

    private fun showIfChanged(target: OverlayTarget) {
        if (shownTarget == target && overlay.isShowing) return
        if (BuildConfig.DEBUG) Log.d(TAG, "show overlay: $target")
        shownTarget = target
        overlay.hide()
        overlay.show {
            when (target) {
                is OverlayTarget.Prompt -> LockPromptOverlay(
                    target = target,
                    settings = settings,
                    onStart = { purpose, minutes ->
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "start pkg=${target.packageName} minutes=$minutes")
                        }
                        scope.launch {
                            container.sessionManager.start(
                                packageName = target.packageName,
                                appLabel = target.appLabel,
                                purpose = purpose,
                                plannedMinutes = minutes,
                            )
                            dismissOverlay()
                        }
                    },
                    onSkip = {
                        endAndBlock(
                            packageName = target.packageName,
                            appLabel = target.appLabel,
                            outcome = SessionOutcome.SKIPPED,
                        )
                    },
                )

                is OverlayTarget.TimeUp -> TimeUpOverlay(
                    target = target,
                    settings = settings,
                    onExtend = { reason, extraMinutes ->
                        scope.launch {
                            container.sessionManager.extend(extraMinutes)
                            // 加时等于开了新的一段额度，提醒要能再响一次
                            reminderSentForSessionId = null
                            dismissOverlay("已加时")
                        }
                    },
                    onFinish = {
                        endAndBlock(
                            packageName = target.session.packageName,
                            appLabel = target.session.appLabel,
                            outcome = SessionOutcome.COMPLETED,
                        )
                    },
                )

                is OverlayTarget.Blocked -> BlockedScreen(
                    appLabel = target.appLabel,
                    // 只尝试；失败时界面留在原地，文案已提示可用系统主屏幕键离开
                    onGoHome = { tryGoHome() },
                )
            }
        }
    }

    private fun dismissOverlay(reason: String = "") {
        if (shownTarget != null && BuildConfig.DEBUG) Log.d(TAG, "dismiss overlay: $reason")
        shownTarget = null
        overlay.hide()
    }

    /**
     * 结束一次使用，并把人挡在外面。
     *
     * 「这次算了」和「时间到 → 结束」**必须走同一条路**——
     * 曾经只给前者加了兜底，结果后者成了死循环：
     * 浮层关掉、回桌面被拦、人还在应用里，下一轮轮询又重新弹问询。
     *
     * 两步走，因为**国产 ROM 常常拦截后台启动 Activity**（HyperOS 3.0 实测如此）：
     * 先试着回桌面；不管成不成，都把浮层切成"已挡住"。
     * 回桌面成功时它会被立刻顶掉，失败时它就是那堵墙——不依赖任何系统放行。
     */
    private fun endAndBlock(packageName: String, appLabel: String, outcome: SessionOutcome) {
        scope.launch {
            container.sessionManager.finish(outcome)
            tryGoHome()
            shownTarget = null
            overlay.hide()
            showIfChanged(OverlayTarget.Blocked(packageName, appLabel))
        }
    }

    private fun tryGoHome() {
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
        }.onFailure { error ->
            // 不再静默吞掉：被 ROM 拦截时这里必须留下痕迹
            Log.w(TAG, "回桌面被系统拒绝，改用浮层兜底", error)
        }
    }

    private fun promoteToForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }
    }

    private fun updateNotification(active: com.timelock.app.domain.session.ActiveSession?) {
        if (active == null) {
            if (lastNotifiedKey != IDLE_KEY) {
                lastNotifiedKey = IDLE_KEY
                notify(buildNotification(null))
            }
            return
        }
        val remainingMinutes = (active.remainingMillis(System.currentTimeMillis()) / 60_000L)
            .coerceAtLeast(0L)
        val key = "${active.id}:$remainingMinutes"
        if (key == lastNotifiedKey) return
        lastNotifiedKey = key
        notify(buildNotification(active))
    }

    private fun notify(notification: Notification) {
        runCatching {
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 到点前提醒。
     *
     * 用户明确说过不要"突然袭击"：还剩 [AppSettings.remindBeforeMinutes] 分钟时先响一次，
     * 让人有时间收尾，而不是被硬生生弹出去。每个会话只提醒一次——
     * 否则 500ms 一轮的轮询会把它变成骚扰。
     */
    private fun maybeSendPreReminder(active: ActiveSession, now: Long) {
        val configured = settings.remindBeforeMinutes
        if (configured <= 0) return
        if (reminderSentForSessionId == active.id) return

        // 提前量还要被会话长度压住。
        // 额度最短只有 3 分钟，若固定提前 5 分钟，那每次一开始计时就会立刻收到
        // "还剩 5 分钟"——信息量为零，还平白吓人一次。
        val total = active.totalMinutes
        if (total <= 1) return // 一分钟的额度没有"提前"的余地
        val leadMinutes = minOf(configured, maxOf(1, total / 3))

        val remaining = active.remainingMillis(now)
        if (remaining > leadMinutes * 60_000L) return

        reminderSentForSessionId = active.id
        val remainingMinutes = (remaining / 60_000L).coerceAtLeast(1L).toInt()
        notifyPreReminder(active, remainingMinutes)
    }

    private fun notifyPreReminder(active: ActiveSession, remainingMinutes: Int) {
        runCatching {
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                ?.notify(REMINDER_NOTIFICATION_ID, buildPreReminderNotification(active, remainingMinutes))
        }
        vibrateBriefly()
    }

    /**
     * 看护已经失效，必须让用户知道。
     *
     * 这是整个应用最容易"假死"的地方：服务还在跑、界面看着正常、权限列表也对，
     * 但拦截其实已经不可能发生了。与其安静地失效，不如打扰一次。
     */
    private fun notifyProtectionLost() {
        val openApp = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock_notification)
            .setContentTitle("时间锁已经停止看护")
            .setContentText("悬浮窗权限被系统收回了，现在拦不住任何应用。点这里重新开启。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "悬浮窗权限被系统收回了，时间锁现在拦不住任何应用。" +
                        "不同机型会不定期回收这个权限，点这里回到应用重新开启即可。",
                ),
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                ?.notify(ALERT_NOTIFICATION_ID, notification)
        }
        vibrateBriefly()
    }

    private fun buildPreReminderNotification(
        active: ActiveSession,
        remainingMinutes: Int,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock_notification)
            .setContentTitle("「${active.appLabel}」还剩 $remainingMinutes 分钟")
            .setContentText("该收尾了。到点会再拦你一次。")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /** 短震一下。没有振动器（模拟器/平板）就安静跳过，不要因此崩掉整条链路。 */
    private fun vibrateBriefly() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun buildNotification(
        active: com.timelock.app.domain.session.ActiveSession?,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (active == null) {
            "正在看护中"
        } else {
            val remaining = (active.remainingMillis(System.currentTimeMillis()) / 60_000L)
                .coerceAtLeast(0L)
            "「${active.appLabel}」还剩 $remaining 分钟"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock_notification)
            .setContentTitle("时间锁")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "看护状态",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "显示时间锁正在看护哪一个应用"
                    setShowBadge(false)
                },
            )
        }

        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "到点前提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "时间快用完时提前提醒一次"
                    enableVibration(false) // 震动由我们自己控制，避免重复
                },
            )
        }

        if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "看护中断提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "悬浮窗权限被收回、看护失效时通知你一次"
                    enableVibration(false)
                },
            )
        }
    }

    companion object {
        private const val TAG = "LockMonitor"
        private const val CHANNEL_ID = "timelock_monitor"
        private const val REMINDER_CHANNEL_ID = "timelock_reminder"
        private const val ALERT_CHANNEL_ID = "timelock_alert"
        private const val NOTIFICATION_ID = 1001
        private const val REMINDER_NOTIFICATION_ID = 1002
        private const val ALERT_NOTIFICATION_ID = 1003
        private const val POLL_INTERVAL_MS = 500L

        /** 看门狗检查间隔，以及判定"轮询卡死"的静默阈值。 */
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val WATCHDOG_TIMEOUT_MS = 15_000L
        private const val IDLE_KEY = "idle"

        /** 连续多少次读不到前台才认定"确实离开了"。6 次 × 500ms = 3 秒。 */
        private const val NULL_STREAK_TOLERANCE = 6

        fun start(context: Context) {
            val intent = Intent(context, LockMonitorService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, LockMonitorService::class.java)) }
        }
    }
}

