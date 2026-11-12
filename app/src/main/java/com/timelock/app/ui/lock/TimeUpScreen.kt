package com.timelock.app.ui.lock

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val EXTRA_MINUTE_OPTIONS = listOf(5, 10, 15)

data class TimeUpUiState(
    val appLabel: String,
    val usedMinutes: Int,
    val reasonTags: List<String>,
    val selectedReason: String? = null,
    val customReason: String = "",
    val extraMinutes: Int = 10,
)

/**
 * 时间到。
 *
 * 关键设计：「加时」不是无条件按钮——**必须先说清楚为什么**。
 * 这是"有代价的逃生门"（SPEC 问题 6 必做项）：真需要时能救急，
 * 但不像没有约束；理由会进入历史记录，事后回看时刺痛你的正是这些理由。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeUpScreen(
    state: TimeUpUiState,
    onReasonSelected: (String) -> Unit,
    onCustomReasonChanged: (String) -> Unit,
    onExtraMinutesChanged: (Int) -> Unit,
    onExtend: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasReason = state.selectedReason != null || state.customReason.isNotBlank()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "时间到了",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "「${state.appLabel}」的 ${state.usedMinutes} 分钟用完了",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            HorizontalDivider()

            Text(
                text = "要加时，先说清楚为什么",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "理由会记进历史。事后回看时，这些理由往往比时长数字更有用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.reasonTags.forEach { tag ->
                    FilterChip(
                        selected = state.selectedReason == tag,
                        onClick = { onReasonSelected(tag) },
                        label = { Text(tag) },
                    )
                }
            }

            OutlinedTextField(
                value = state.customReason,
                onValueChange = onCustomReasonChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("或者自己写一句") },
                singleLine = true,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "再加", style = MaterialTheme.typography.bodyMedium)
                EXTRA_MINUTE_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = state.extraMinutes == minutes,
                        onClick = { onExtraMinutesChanged(minutes) },
                        label = { Text("$minutes 分钟") },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onExtend,
                enabled = hasReason,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (hasReason) "加 ${state.extraMinutes} 分钟" else "先选一个理由")
            }

            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 不写"回到桌面"：国产 ROM 常常拦截后台启动 Activity，
                // 我们无法保证把人送回去，只能保证把他挡在这里。
                Text("结束这次使用")
            }
        }
    }
}
