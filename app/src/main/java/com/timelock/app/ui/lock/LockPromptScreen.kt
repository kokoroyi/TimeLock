package com.timelock.app.ui.lock

import com.timelock.app.domain.lock.MathAnswerState

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 拦截界面 v2 —— 逐段解锁。
 *
 * 改这一版的原因：一屏把所有选项都摆出来时，用户会靠肌肉记忆连点三下就进去了，
 * 「先想清楚为什么」这个灵魂机制等于没有。所以改成**前一段没填完，后一段根本不存在**。
 *
 * 规则：
 * 1. 「这次要做什么？」必须先有答案（点一个标签，或在输入框里写一句）；
 * 2. 有标签时，「自定义」是胶囊，点开才出输入框；**一个标签都没有时直接出输入框**
 *    ——那时唯一的选择就是自己写，不该让用户再多点一次；
 * 3. 目的有了之后「打算用多久？」才出现；
 * 4. 时长选完、且设置里开了「计算验证码」时，第三段「算一下」才出现，答对才放行；
 * 5. 以后要加第四、第五组，照 [GateSection] 的写法接在后面即可——
 *    每一段的开关由**紧邻前一段**是否完成决定，不要各自判断。
 *
 * 这一版只是展示层：所有状态由外部传入，不接任何业务逻辑。
 */
@Composable
fun LockPromptScreen(
    appLabel: String,
    purposeTags: List<String>,
    selectedPurpose: String?,
    customPurpose: String,
    onPurposeSelected: (String) -> Unit,
    onCustomPurposeChanged: (String) -> Unit,
    selectedMinutes: Int?,
    customMinutesText: String,
    onMinutesSelected: (Int) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    mathChallengeEnabled: Boolean,
    mathProblem: String,
    mathAnswer: String,
    mathAnswerState: MathAnswerState,
    onMathAnswerChanged: (String) -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 链式解锁：每一段的"完成"都必须建立在**前面所有段落都完成**之上。
    // 踩过的坑：第三段曾经只判断 durationDone，结果用户回头取消用途时，
    // 第二段收起了、第三段却还赖在屏幕上，状态自相矛盾。
    val purposeDone = selectedPurpose != null || customPurpose.isNotBlank()
    val durationUnlocked = purposeDone
    val effectiveMinutes = customMinutesText.toIntOrNull() ?: selectedMinutes
    val durationDone = durationUnlocked && effectiveMinutes != null && effectiveMinutes > 0
    val mathUnlocked = durationDone
    val mathDone = !mathChallengeEnabled || mathAnswerState == MathAnswerState.CORRECT
    val canStart = purposeDone && durationDone && mathDone

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(appLabel = appLabel)

            // 第一段：永远可用
            PurposeSection(
                purposeTags = purposeTags,
                selectedPurpose = selectedPurpose,
                customPurpose = customPurpose,
                done = purposeDone,
                onPurposeSelected = onPurposeSelected,
                onCustomPurposeChanged = onCustomPurposeChanged,
            )

            // 第二段：第一段没填完就整段不出现。
            // 选"不显示"而不是"变暗"：暗掉的控件仍然占着位置、还在吸引视线，
            // 而这里的目的就是让后一步在条件满足前根本不存在。
            GateSection(enabled = durationUnlocked) {
                // 这里必须自带一层列间距：AnimatedVisibility 内部的子项吃不到外层 Column 的
                // spacedBy，否则分隔线会紧贴着下面的标题（就是这么挤出来的）。
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionDivider()
                    DurationSection(
                        selectedMinutes = selectedMinutes,
                        customMinutesText = customMinutesText,
                        onMinutesSelected = onMinutesSelected,
                        onCustomMinutesChanged = onCustomMinutesChanged,
                    )
                }
            }

            // 第三段：可选的计算验证码，只在设置里启用时存在；前面两段都完成才出现。
            GateSection(enabled = mathChallengeEnabled && mathUnlocked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionDivider()
                    MathChallengeSection(
                        problem = mathProblem,
                        answer = mathAnswer,
                        answerState = mathAnswerState,
                        onAnswerChanged = onMathAnswerChanged,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Actions(canStart = canStart, onStart = onStart, onSkip = onSkip)
        }
    }
}

/**
 * 计算验证码：答对才放行。
 *
 * 定位是把"肌肉记忆连点"彻底打断——前两段还能靠反射完成，这一段必须真的动脑。
 * 答错不换题：换题等于允许重新抽一道简单的。
 */
@Composable
private fun MathChallengeSection(
    problem: String,
    answer: String,
    answerState: MathAnswerState,
    onAnswerChanged: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "算一下",
            trailing = if (answerState == MathAnswerState.CORRECT) null else "答对才能进",
            trailingHighlight = false,
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "$problem = ？",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }

        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("答案") },
            singleLine = true,
            isError = answerState == MathAnswerState.WRONG,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
        )

        when (answerState) {
            MathAnswerState.WRONG -> Text(
                text = "答案不对，再算算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            MathAnswerState.CORRECT -> Text(
                text = "答对了。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )

            MathAnswerState.NEUTRAL -> Unit
        }
    }
}

/**
 * 逐段解锁的通用外壳。
 *
 * 后继段落套这个：传入"前一段是否完成"，未完成时整段不渲染。
 * 以后加第三、第四段时，[enabled] 一律由**前面所有段落都完成**决定，不要各自判断。
 *
 * 出现/收起都带动画：条件满足的那一刻如果内容直接"蹦"出来把按钮顶下去，观感很跳。
 *
 * **出现后还要自己滚进可见区**：真机上用户一边打字（键盘占掉半屏）一边解锁下一段，
 * 新段落会落在键盘外面——屏幕上看起来什么都没变，很容易以为卡住了。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GateSection(enabled: Boolean, content: @Composable () -> Unit) {
    val scrollRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(enabled) {
        if (enabled) {
            // 等展开动画把高度铺开再滚，否则滚到的还是折叠前的尺寸
            delay(140)
            runCatching { scrollRequester.bringIntoView() }
        }
    }

    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(160)),
    ) {
        Box(modifier = Modifier.bringIntoViewRequester(scrollRequester)) {
            content()
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .alpha(0.5f),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {}
    }
}

@Composable
private fun Header(appLabel: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = appLabel.take(1),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = "打开「$appLabel」",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "先说清楚要做什么，再决定花多久。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposeSection(
    purposeTags: List<String>,
    selectedPurpose: String?,
    customPurpose: String,
    done: Boolean,
    onPurposeSelected: (String) -> Unit,
    onCustomPurposeChanged: (String) -> Unit,
) {
    // 一个标签都没有的时候，不要再让用户先点一次「自定义」——
    // 那时唯一的选项就是自己写，直接把输入框摆出来。
    val hasTags = purposeTags.isNotEmpty()
    var customExpanded by remember { mutableStateOf(false) }
    val showCustomField = !hasTags || customExpanded
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "这次要做什么？",
            trailing = if (done) null else "必选",
            trailingHighlight = !done,
        )

        if (hasTags) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                purposeTags.forEach { tag ->
                    FilterChip(
                        selected = selectedPurpose == tag,
                        onClick = {
                            customExpanded = false
                            onCustomPurposeChanged("")
                            onPurposeSelected(tag)
                        },
                        label = { Text(tag) },
                    )
                }
                FilterChip(
                    selected = customExpanded,
                    onClick = { customExpanded = !customExpanded },
                    label = { Text("自定义") },
                )
            }
        }

        if (showCustomField) {
            OutlinedTextField(
                value = customPurpose,
                onValueChange = onCustomPurposeChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("写下你的理由") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSection(
    selectedMinutes: Int?,
    customMinutesText: String,
    onMinutesSelected: (Int) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
) {
    var customExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "打算用多久？",
            trailing = null,
            trailingHighlight = false,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DURATION_PRESETS.forEach { minutes ->
                FilterChip(
                    selected = selectedMinutes == minutes && !customExpanded,
                    onClick = {
                        customExpanded = false
                        onCustomMinutesChanged("")
                        onMinutesSelected(minutes)
                    },
                    label = { Text("$minutes 分钟") },
                )
            }
            FilterChip(
                selected = customExpanded,
                onClick = { customExpanded = !customExpanded },
                label = { Text("自定义") },
            )
        }

        if (customExpanded) {
            OutlinedTextField(
                value = customMinutesText,
                onValueChange = onCustomMinutesChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分钟数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: String?,
    trailingHighlight: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = if (trailingHighlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Actions(
    canStart: Boolean,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("开始计时")
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("这次算了")
        }
    }
}

/** 时长预设。实测最常用的其实是"三五分钟看一下"，10/15/30/60 那种排法用不上。 */
private val DURATION_PRESETS = listOf(3, 5, 10)
