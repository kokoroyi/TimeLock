package com.timelock.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timelock.app.data.history.UsageHistoryStore
import com.timelock.app.data.prefs.SettingsStore
import com.timelock.app.domain.model.AppSettings
import com.timelock.app.domain.model.SessionOutcome
import com.timelock.app.domain.model.UsageSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class HistoryRange(val label: String) {
    TODAY("今日"),
    WEEK("本周"),
    ALL("全部"),
}

data class HistoryEntry(
    val id: String,
    val appLabel: String,
    val timeLabel: String,
    val dateLabel: String,
    val purpose: String,
    val plannedMinutes: Int,
    val actualMinutes: Int?,
    val outcome: SessionOutcome,
    val extensions: Int,
)

data class HistoryInsights(
    val sessionCount: Int,
    val totalMinutes: Int,
    val reflexiveCount: Int,
    val reflexivePercent: Int,
    val topPurposeLabel: String?,
    val topPurposeCount: Int,
    val extensionCount: Int,
)

data class HistoryUiState(
    val range: HistoryRange = HistoryRange.WEEK,
    val insights: HistoryInsights = HistoryInsights(0, 0, 0, 0, null, 0, 0),
    val entries: List<HistoryEntry> = emptyList(),
)

class HistoryViewModel(
    historyStore: UsageHistoryStore,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val range = MutableStateFlow(HistoryRange.WEEK)

    val uiState: StateFlow<HistoryUiState> = combine(
        historyStore.sessions,
        settingsStore.settings,
        range,
    ) { sessions, settings, selectedRange ->
        build(sessions, settings, selectedRange)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun setRange(value: HistoryRange) {
        range.value = value
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE")

private fun build(
    sessions: List<UsageSession>,
    settings: AppSettings,
    range: HistoryRange,
): HistoryUiState {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val from: LocalDate? = when (range) {
        HistoryRange.TODAY -> today
        HistoryRange.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        HistoryRange.ALL -> null
    }
    val fromMillis = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE

    val scoped = sessions
        .filter { it.startedAt >= fromMillis }
        .sortedByDescending { it.startedAt }

    val reflexiveTags = settings.reflexiveTags
    val reflexive = scoped.count { it.purpose in reflexiveTags }
    val totalMinutes = scoped.sumOf { it.actualMinutes ?: it.plannedMinutes }

    val topPurpose = scoped
        .map { it.purpose }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }

    val insights = HistoryInsights(
        sessionCount = scoped.size,
        totalMinutes = totalMinutes,
        reflexiveCount = reflexive,
        reflexivePercent = if (scoped.isEmpty()) 0 else reflexive * 100 / scoped.size,
        topPurposeLabel = topPurpose?.key,
        topPurposeCount = topPurpose?.value ?: 0,
        extensionCount = scoped.count { it.extensions > 0 },
    )

    val entries = scoped.map { session ->
        val date = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
        val time = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalTime()
        val dateLabel = when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> date.format(DATE_FORMAT)
        }
        HistoryEntry(
            id = session.id,
            appLabel = session.appLabel,
            timeLabel = time.format(TIME_FORMAT),
            dateLabel = dateLabel,
            purpose = session.purpose,
            plannedMinutes = session.plannedMinutes,
            actualMinutes = session.actualMinutes,
            outcome = session.outcome,
            extensions = session.extensions,
        )
    }

    return HistoryUiState(range = range, insights = insights, entries = entries)
}
