package com.timelock.app.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timelock.app.ui.LocalAppContainer
import com.timelock.app.ui.common.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAppsScreen(
    mode: PickerMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val vm: SelectAppsViewModel = viewModel(
        key = mode.name,
        factory = viewModelFactory {
            initializer {
                SelectAppsViewModel(container.appCatalog, container.settingsStore, mode)
            }
        },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val title = when (mode) {
        PickerMode.MANAGED -> "选择要管理的应用"
        PickerMode.WHITELIST -> "白名单：这些应用永不拦截"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            if (mode == PickerMode.WHITELIST) {
                Text(
                    text = "导航、支付、电话这类不该被拦的应用放这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("搜索应用") },
                singleLine = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "已选 ${state.selectedCount} 个",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.hiddenSystemCount > 0) {
                        Text(
                            text = "已隐藏 ${state.hiddenSystemCount} 个系统应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "显示系统应用",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.showSystemApps,
                    onCheckedChange = vm::onShowSystemAppsChanged,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (state.loading && state.rows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.packageName }) { row ->
                        AppRowItem(row = row, onToggle = { vm.toggle(row.packageName, row.label) })
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 72.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRowItem(row: AppRow, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(bitmap = row.icon, fallbackText = row.label)

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = row.label, style = MaterialTheme.typography.bodyLarge)
            // 只在勾了"显示系统应用"之后才有意义，所以只在系统应用上挂这个标记。
            // 包名不再显示：用户靠图标和名字认应用，那串英文标识只会占地方。
            if (row.isSystem) {
                Text(
                    text = "系统",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(checked = row.selected, onCheckedChange = { onToggle() })
    }
}
