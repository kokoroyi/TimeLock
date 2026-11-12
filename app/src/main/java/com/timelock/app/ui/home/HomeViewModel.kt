package com.timelock.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.timelock.app.data.history.UsageHistoryStore
import com.timelock.app.data.prefs.SettingsStore
import com.timelock.app.domain.model.ManagedApp
import com.timelock.app.domain.model.UsageSession
import com.timelock.app.system.InstalledAppCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class AppUsageSummary(
    val app: ManagedApp,
    val minutesToday: Int,
    val remainingMinutes: Int?,
    val icon: Bitmap?,
)

data class HomeUiState(
    val loading: Boolean = true,
    val apps: List<AppUsageSummary> = emptyList(),
    val todayTotalMinutes: Int = 0,
    val todaySessionCount: Int = 0,
)

class HomeViewModel(
    settingsStore: SettingsStore,
    historyStore: UsageHistoryStore,
    appCatalog: InstalledAppCatalog,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        settingsStore.managedApps,
        historyStore.sessions,
    ) { apps, sessions ->
        val todayStart = startOfTodayMillis()
        val todaySessions = sessions.filter { it.startedAt >= todayStart }

        val summaries = apps
            .sortedBy { it.addedAt }
            .map { app ->
                val minutes = todaySessions
                    .filter { it.packageName == app.packageName }
                    .sumOf { sessionMinutes(it) }
                val remaining = app.dailyLimitMinutes?.let { (it - minutes).coerceAtLeast(0) }
                AppUsageSummary(
                    app = app,
                    minutesToday = minutes,
                    remainingMinutes = remaining,
                    // 这里能直接 await：combine 的 transform 本身就是挂起函数
                    icon = appCatalog.iconOf(app.packageName),
                )
            }

        HomeUiState(
            loading = false,
            apps = summaries,
            todayTotalMinutes = summaries.sumOf { it.minutesToday },
            todaySessionCount = todaySessions.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

/** 优先用结算后的实际时长；进行中的会话按"承诺时长"计，避免今日数字忽大忽小。 */
private fun sessionMinutes(session: UsageSession): Int =
    session.actualMinutes ?: session.plannedMinutes

private fun startOfTodayMillis(): Long =
    LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
