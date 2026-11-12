package com.timelock.app.di

import android.content.Context
import com.timelock.app.data.history.UsageHistoryStore
import com.timelock.app.data.prefs.SettingsStore
import com.timelock.app.domain.session.SessionManager
import com.timelock.app.system.ForegroundAppDetector
import com.timelock.app.system.InstalledAppCatalog
import com.timelock.app.system.PermissionChecker

/**
 * 极简手工依赖容器。
 *
 * 规模不足以引入 Hilt/Koin，也不希望为此增加注解处理器——
 * 在 AGP 9 内置 Kotlin 的新工具链下，少一个编译器插件就少一类构建风险。
 *
 * 注意：容器实例挂在 Application 上，Service 与 UI 共享同一份，
 * 因此"当前会话"只有一个真相来源。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val settingsStore: SettingsStore = SettingsStore(appContext)

    val historyStore: UsageHistoryStore = UsageHistoryStore(appContext)

    val permissionChecker: PermissionChecker = PermissionChecker(appContext)

    val appCatalog: InstalledAppCatalog = InstalledAppCatalog(appContext)

    val foregroundAppDetector: ForegroundAppDetector = ForegroundAppDetector(appContext)

    val sessionManager: SessionManager = SessionManager(historyStore)
}
