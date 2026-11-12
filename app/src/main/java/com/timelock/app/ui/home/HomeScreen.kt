package com.timelock.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timelock.app.ui.LocalAppContainer
import com.timelock.app.ui.common.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddApp: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(container.settingsStore, container.historyStore, container.appCatalog)
            }
        },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("时间锁") },
                actions = {
                    TextButton(onClick = onOpenHistory) { Text("历史") }
                    TextButton(onClick = onOpenSettings) { Text("设置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddApp,
                content = { Text("添加应用") },
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
            TodayOverview(
                totalMinutes = state.todayTotalMinutes,
                appCount = state.apps.size,
                sessionCount = state.todaySessionCount,
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "受管应用",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (state.apps.isEmpty() && !state.loading) {
                EmptyState(onAddApp = onAddApp)
            } else {
                state.apps.forEach { summary -> ManagedAppCard(summary) }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun TodayOverview(
    totalMinutes: Int,
    appCount: Int,
    sessionCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "今日总览", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$totalMinutes",
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    text = " 分钟",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = if (appCount == 0) {
                    "还没有受管应用"
                } else {
                    "$appCount 个应用受管 · 今日打开 $sessionCount 次"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyState(onAddApp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "还没有受管应用",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "把容易刷上头的应用加进来。之后每次打开它，都需要先说明这次要做什么、用多久。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAddApp) { Text("添加第一个应用") }
        }
    }
}

@Composable
private fun ManagedAppCard(summary: AppUsageSummary) {
    val app = summary.app
    val progress = app.dailyLimitMinutes
        ?.takeIf { it > 0 }
        ?.let { (summary.minutesToday.toFloat() / it).coerceIn(0f, 1f) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(
                bitmap = summary.icon,
                fallbackText = app.label,
                size = 44.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = app.label, style = MaterialTheme.typography.titleMedium)

                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
                        strokeCap = StrokeCap.Round,
                    )
                }

                Text(
                    text = buildString {
                        append("今日 ${summary.minutesToday} 分钟")
                        summary.remainingMinutes?.let { append(" · 剩 $it 分钟") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
