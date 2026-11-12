package com.timelock.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timelock.app.domain.model.SessionOutcome
import com.timelock.app.ui.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(container.historyStore, container.settingsStore)
            }
        },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("使用历史") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                InsightsCard(insights = state.insights, rangeLabel = state.range.label)
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    HistoryRange.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = state.range == option,
                            onClick = { vm.setRange(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = HistoryRange.entries.size,
                            ),
                        ) {
                            Text(option.label)
                        }
                    }
                }
            }

            if (state.entries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(
                            text = "这段时间还没有记录。每次打开受管应用并选定用途后，这里就会留下一条。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(state.entries, key = { it.id }) { entry ->
                    HistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: HistoryInsights, rangeLabel: String) {
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
            Text(text = "$rangeLabel · 打开 ${insights.sessionCount} 次", style = MaterialTheme.typography.labelLarge)

            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "${insights.totalMinutes}", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = " 分钟",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (insights.reflexiveCount > 0) {
                // 整页最有用的一句话：不是"你用了多久"，而是"你为什么打开"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = "其中 ${insights.reflexiveCount} 次是「无聊，随手点开」或「习惯性打开」，" +
                            "占 ${insights.reflexivePercent}%。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            insights.topPurposeLabel?.let { label ->
                Text(
                    text = "最常写下的理由是「$label」（${insights.topPurposeCount} 次）",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (insights.extensionCount > 0) {
                Text(
                    text = "有 ${insights.extensionCount} 次用了加时",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    Column {
        Text(
            text = "${entry.dateLabel} · ${entry.timeLabel}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = outcomeLabel(entry),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                if (entry.purpose.isNotBlank()) {
                    Text(
                        text = "「${entry.purpose}」",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "（未填写用途）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                Text(
                    text = buildString {
                        append("承诺 ${entry.plannedMinutes} 分钟")
                        entry.actualMinutes?.let { append(" · 实际 $it 分钟") }
                        if (entry.extensions > 0) append(" · 加时 ${entry.extensions} 分钟")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun outcomeLabel(entry: HistoryEntry): String = when (entry.outcome) {
    SessionOutcome.COMPLETED -> "用满"
    SessionOutcome.FINISHED_EARLY -> "提前结束"
    SessionOutcome.SKIPPED -> "当场放弃"
    SessionOutcome.EXTENDED -> "加时"
    SessionOutcome.IN_PROGRESS -> "进行中"
}
