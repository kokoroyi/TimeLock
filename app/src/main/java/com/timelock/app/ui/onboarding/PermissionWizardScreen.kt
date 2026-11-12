package com.timelock.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timelock.app.system.DeviceBrand
import com.timelock.app.system.PermissionGuide
import com.timelock.app.system.PermissionKind
import com.timelock.app.system.PermissionStatus
import com.timelock.app.ui.LocalAppContainer

private val ORDER = listOf(
    PermissionKind.USAGE_ACCESS,
    PermissionKind.OVERLAY,
    PermissionKind.NOTIFICATIONS,
    PermissionKind.BATTERY_OPTIMIZATION,
)

private fun titleOf(kind: PermissionKind): String = when (kind) {
    PermissionKind.USAGE_ACCESS -> "使用情况访问"
    PermissionKind.OVERLAY -> "悬浮窗"
    PermissionKind.NOTIFICATIONS -> "通知"
    PermissionKind.BATTERY_OPTIMIZATION -> "电池不受限制"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionWizardScreen(
    onAllGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val vm: PermissionWizardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PermissionWizardViewModel(container.permissionChecker) }
        },
    )
    val context = LocalContext.current
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val byKind = remember(statuses) { statuses.associateBy { it.kind } }

    // 这一项无法用 API 自检，只能让用户手动确认。
    // 但它恰恰是"锁不住"最常见的真凶，所以必须卡住"开始使用"这个按钮。
    var backgroundConfirmed by remember { mutableStateOf(false) }

    // 从系统设置返回时立刻复检
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { vm.refresh() }

    fun request(kind: PermissionKind) {
        val needsRuntimePermission =
            kind == PermissionKind.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (needsRuntimePermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.settingsIntent(kind)?.let(context::startActivity)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("准备工作") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "4 项系统授权 + 1 项后台设置。",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "最后那一项是所有安卓手机的通病，也是最容易被漏掉、后果最严重的一项：" +
                    "少了它，前面四项全绿也拦不住。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "这些权限只在本机使用，本应用没有任何联网功能，也不会读取或上传你的任何数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = { },
                label = { Text("已按你的机型准备：${vm.brand.displayName}") },
            )

            Spacer(Modifier.height(4.dp))

            ORDER.forEach { kind ->
                val status = byKind[kind] ?: PermissionStatus(kind, granted = false)
                PermissionCard(
                    status = status,
                    brand = vm.brand,
                    onRequest = { request(kind) },
                )
            }

            BackgroundUnrestrictedCard(
                brand = vm.brand,
                confirmed = backgroundConfirmed,
                onConfirmChange = { backgroundConfirmed = it },
                onOpenSettings = { context.startActivity(vm.appDetailsIntent()) },
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onAllGranted,
                enabled = vm.allGranted() && backgroundConfirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    when {
                        !vm.allGranted() -> "还有权限未开启"
                        !backgroundConfirmed -> "还有一项需要你确认"
                        else -> "全部就绪，开始使用"
                    },
                )
            }

            Text(
                text = "提示：各厂商的菜单层级会随系统版本变化。上面的一键跳转是可靠的，" +
                    "下面的文字步骤仅供找不到入口时参考。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    status: PermissionStatus,
    brand: DeviceBrand,
    onRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val granted = status.granted

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (granted) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = titleOf(status.kind),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(granted)
            }

            Text(
                text = PermissionGuide.rationale(status.kind),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!granted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onRequest) { Text("去开启") }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起步骤" else "查看步骤")
                    }
                }
            }

            AnimatedVisibility(visible = expanded && !granted) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PermissionGuide.steps(status.kind, brand)
                        .forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    Text(
                        text = "（以上为 ${brand.displayName} 机型步骤）",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * 「后台不被限制」。
 *
 * 刻意不叫"权限"——它不是 permission，任何 API 都读不到它，因此在界面上也和上面四张卡
 * 长得不一样：没有"已开启/未开启"的自动检测，只有一个「去设置」和一个需要用户自己勾的确认框。
 *
 * 之所以还要卡住"开始使用"按钮：这一项没做，前面四项全绿也没用。
 * 实测小米 HyperOS 3.0，漏掉它就会表现为"装完能拦、用一会儿就不拦"，
 * 而用户从界面上完全看不出问题出在哪。
 */
@Composable
private fun BackgroundUnrestrictedCard(
    brand: DeviceBrand,
    confirmed: Boolean,
    onConfirmChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (confirmed) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
            contentColor = if (confirmed) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "后台不被限制",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(confirmed)
            }

            Text(
                text = "所有机型都要做",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = PermissionGuide.backgroundRationale(brand),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onOpenSettings) { Text("去设置") }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起步骤" else "查看步骤")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PermissionGuide.backgroundSteps(brand).forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = "（以上为 ${brand.displayName} 机型步骤）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 整行可点：只让勾选框本身可点的话，点文字没反应，用户会以为卡住了
                    .clickable { onConfirmChange(!confirmed) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // onCheckedChange = null：点击交给整行处理，避免与行点击重复触发
                Checkbox(checked = confirmed, onCheckedChange = null)
                Text(
                    text = "我已经设置好了",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(granted: Boolean) {    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (granted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        contentColor = if (granted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Text(
            text = if (granted) "已开启" else "未开启",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
