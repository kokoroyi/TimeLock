package com.timelock.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.timelock.app.BuildConfig
import com.timelock.app.data.history.UsageHistoryStore
import com.timelock.app.data.prefs.SettingsStore
import com.timelock.app.domain.model.AppSettings
import com.timelock.app.domain.model.ThemeMode
import com.timelock.app.system.DeviceBrand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val historyStore: UsageHistoryStore,
    private val packageName: String,
) : ViewModel() {

    val brand: DeviceBrand = DeviceBrand.detect()

    /**
     * 跳到本应用的「应用信息」页。
     *
     * 「后台不被限制」是"锁不住"最常见的真凶，却无法用 API 自检，
     * 因此它必须能随时从设置页找回来——不能只在首次引导里出现一次。
     */
    fun appDetailsIntent(): Intent {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    val versionName: String = BuildConfig.VERSION_NAME

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setMathChallengeEnabled(enabled: Boolean) = update { it.copy(mathChallengeEnabled = enabled) }

    fun setRemindBefore(minutes: Int) = update { it.copy(remindBeforeMinutes = minutes) }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            if (current.purposeTags.contains(trimmed)) {
                current
            } else {
                current.copy(purposeTags = current.purposeTags + trimmed)
            }
        }
    }

    fun removeTag(tag: String) = update { current ->
        current.copy(
            purposeTags = current.purposeTags.filterNot { it == tag },
            // 被删掉的标签不应再被当作"反射性打开"来统计
            reflexiveTags = current.reflexiveTags.filterNot { it == tag },
        )
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.updateSettings(transform) }
    }
}
