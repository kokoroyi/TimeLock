package com.timelock.app.ui.apps

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timelock.app.data.prefs.SettingsStore
import com.timelock.app.domain.model.ManagedApp
import com.timelock.app.system.InstalledAppCatalog
import com.timelock.app.system.InstalledAppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 同一个选择界面服务两种用途：加入受管列表 / 加入白名单。 */
enum class PickerMode {
    MANAGED,
    WHITELIST,
}

data class AppRow(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val selected: Boolean,
    val icon: Bitmap?,
)

data class SelectAppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val showSystemApps: Boolean = false,
    val rows: List<AppRow> = emptyList(),
    val selectedCount: Int = 0,
    /** 被"默认不显示系统应用"挡掉了多少个，用来提示用户去哪里找。 */
    val hiddenSystemCount: Int = 0,
)

class SelectAppsViewModel(
    private val appCatalog: InstalledAppCatalog,
    private val settingsStore: SettingsStore,
    private val mode: PickerMode,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val query = MutableStateFlow("")

    /**
     * 默认不显示系统应用。
     *
     * 绝大多数人不会去限制系统自带的应用，把它们混在一起只会让列表长出好几倍、
     * 真正想找的那个反而难找。需要时右下角一个开关就能调出来。
     */
    private val showSystemApps = MutableStateFlow(false)

    val uiState: StateFlow<SelectAppsUiState> = combine(
        installed,
        settingsStore.managedApps,
        settingsStore.settings,
        loading,
        combine(query, showSystemApps) { searchText, showSystem -> searchText to showSystem },
    ) { apps, managedApps, settings, isLoading, (searchText, showSystem) ->
        val selectedPackages = when (mode) {
            PickerMode.MANAGED -> managedApps.map { it.packageName }.toSet()
            PickerMode.WHITELIST -> settings.whitelistPackages
        }

        val filtered = if (searchText.isBlank()) {
            // 翻列表时过滤，减少干扰；已选中的应用永远显示，
            // 否则会出现"选了却找不回来、取消不掉"的死角。
            apps.filter { showSystem || !it.isSystem || it.packageName in selectedPackages }
        } else {
            // 一旦开始搜索，就搜全部——过滤器是给"翻"用的，不该让"找"找不到东西。
            // 包名也参与匹配：想精确定位某个应用时，输包名最准。
            apps.filter {
                it.label.contains(searchText, ignoreCase = true) ||
                    it.packageName.contains(searchText, ignoreCase = true)
            }
        }

        SelectAppsUiState(
            loading = isLoading,
            query = searchText,
            showSystemApps = showSystem,
            rows = filtered.map { app ->
                AppRow(
                    packageName = app.packageName,
                    label = app.label,
                    isSystem = app.isSystem,
                    selected = app.packageName in selectedPackages,
                    icon = app.icon,
                )
            },
            selectedCount = selectedPackages.size,
            hiddenSystemCount = if (showSystem || searchText.isNotBlank()) 0 else apps.count { it.isSystem },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SelectAppsUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            installed.value = appCatalog.loadLaunchableApps()
            loading.value = false
        }
    }

    fun onQueryChanged(value: String) {
        query.value = value
    }

    fun onShowSystemAppsChanged(value: Boolean) {
        showSystemApps.value = value
    }

    fun toggle(packageName: String, label: String) {
        viewModelScope.launch {
            when (mode) {
                PickerMode.MANAGED -> settingsStore.updateManagedApps { current ->
                    if (current.any { it.packageName == packageName }) {
                        current.filterNot { it.packageName == packageName }
                    } else {
                        current + ManagedApp(
                            packageName = packageName,
                            label = label,
                            addedAt = System.currentTimeMillis(),
                        )
                    }
                }

                PickerMode.WHITELIST -> settingsStore.updateSettings { current ->
                    val next = current.whitelistPackages.toMutableSet()
                    if (!next.add(packageName)) next.remove(packageName)
                    current.copy(whitelistPackages = next)
                }
            }
        }
    }
}
