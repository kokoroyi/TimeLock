package com.timelock.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timelock.app.domain.model.AppSettings
import com.timelock.app.domain.model.ThemeMode
import com.timelock.app.system.PermissionGuide
import com.timelock.app.ui.LocalAppContainer

private val REMIND_OPTIONS = listOf(0 to "不提醒", 5 to "5 分钟前", 10 to "10 分钟前")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenWhitelist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(container.settingsStore, container.historyStore, context.packageName)
            }
        },
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    var newTag by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("外观")

            Text("深色模式", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) {
                        Text(themeLabel(mode))
                    }
                }
            }

            SwitchRow(
                title = "跟随系统壁纸取色",
                subtitle = "Android 12 及以上可用；关闭后使用应用自带的靛蓝配色",
                checked = settings.dynamicColor,
                onCheckedChange = vm::setDynamicColor,
            )

            HorizontalDivider()
            SectionTitle("行为")

            SwitchRow(
                title = "计算验证码",
                subtitle = "进入受管应用前先算一道百以内的口算题，答对才放行",
                checked = settings.mathChallengeEnabled,
                onCheckedChange = vm::setMathChallengeEnabled,
            )

            Text("到点前提醒", style = MaterialTheme.typography.bodyLarge)
            Text(
                // 额度短的时候固定提前 5 分钟等于一开就响，所以实际提前量会被会话长度压住
                text = "额度短时会按比例缩小提前量，不会一开计时就响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMIND_OPTIONS.forEach { (minutes, label) ->
                    FilterChip(
                        selected = settings.remindBeforeMinutes == minutes,
                        onClick = { vm.setRemindBefore(minutes) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider()
            SectionTitle("意图标签")
            Text(
                text = "点一下标签即可删除。「无聊，随手点开」「习惯性打开」是洞察页的关键，建议保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                settings.purposeTags.forEach { tag ->
                    AssistChip(
                        onClick = { vm.removeTag(tag) },
                        label = { Text("$tag ×") },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("新增标签") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        vm.addTag(newTag)
                        newTag = ""
                    },
                    enabled = newTag.isNotBlank(),
                ) {
                    Text("添加")
                }
            }

            HorizontalDivider()
            SectionTitle("后台不被限制")
            Text(
                text = PermissionGuide.backgroundRationale(vm.brand),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { context.startActivity(vm.appDetailsIntent()) }) {
                Text("打开应用信息页")
            }
            var backgroundStepsExpanded by remember { mutableStateOf(false) }
            TextButton(onClick = { backgroundStepsExpanded = !backgroundStepsExpanded }) {
                Text(if (backgroundStepsExpanded) "收起步骤" else "查看设置步骤")
            }
            AnimatedVisibility(visible = backgroundStepsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PermissionGuide.backgroundSteps(vm.brand).forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionTitle("白名单")
            Text(
                text = "已豁免 ${settings.whitelistPackages.size} 个应用",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenWhitelist) { Text("管理白名单") }

            HorizontalDivider()
            SectionTitle("数据")
            TextButton(onClick = { confirmClear = true }) { Text("清除全部使用历史") }

            HorizontalDivider()
            SectionTitle("关于")
            Text(
                text = "时间锁 ${vm.versionName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "本应用完全离线：没有任何联网功能，不会读取或上传你的任何数据。" +
                    "所有记录只保存在这台设备上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除全部使用历史？") },
            text = { Text("意图记录与时长统计会一起消失，无法恢复。受管应用列表不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    confirmClear = false
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.FOLLOW_SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "常亮"
    ThemeMode.DARK -> "常暗"
}
