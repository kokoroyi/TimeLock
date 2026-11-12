package com.timelock.app.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.timelock.app.domain.lock.MathAnswerState
import com.timelock.app.domain.lock.MathChallengeGenerator
import com.timelock.app.domain.lock.check
import com.timelock.app.domain.model.AppSettings
import com.timelock.app.domain.session.ActiveSession
import com.timelock.app.ui.lock.BlockedScreen
import com.timelock.app.ui.lock.LockPromptScreen
import com.timelock.app.ui.lock.TimeUpScreen
import com.timelock.app.ui.lock.TimeUpUiState

/** 悬浮窗要显示的三种界面。 */
sealed interface OverlayTarget {
    data class Prompt(val packageName: String, val appLabel: String) : OverlayTarget
    data class TimeUp(val session: ActiveSession) : OverlayTarget

    /** 「这次算了」之后：回桌面若被系统拦住，就由它兜住。 */
    data class Blocked(val packageName: String, val appLabel: String) : OverlayTarget
}

/**
 * 拦截界面（逐段解锁版）。
 *
 * 三段顺序：这次要做什么 → 打算用多久 → 算一下（可选）。
 * 计算验证码的题目在**这个悬浮窗的生命周期内只生成一次**：答错不换题，
 * 换题等于允许用户重新抽一道简单的，这道关就白设了。
 */
@Composable
fun LockPromptOverlay(
    target: OverlayTarget.Prompt,
    settings: AppSettings,
    onStart: (purpose: String, minutes: Int) -> Unit,
    onSkip: () -> Unit,
) {
    var selectedPurpose by remember { mutableStateOf<String?>(null) }
    var customPurpose by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableStateOf<Int?>(null) }
    var customMinutes by remember { mutableStateOf("") }
    var mathAnswer by remember { mutableStateOf("") }

    val challenge = remember(settings.mathChallengeEnabled) {
        if (settings.mathChallengeEnabled) MathChallengeGenerator.generate() else null
    }

    LockPromptScreen(
        appLabel = target.appLabel,
        purposeTags = settings.purposeTags,
        selectedPurpose = selectedPurpose,
        customPurpose = customPurpose,
        onPurposeSelected = { tag ->
            selectedPurpose = if (selectedPurpose == tag) null else tag
            customPurpose = ""
        },
        onCustomPurposeChanged = { text ->
            customPurpose = text
            if (text.isNotBlank()) selectedPurpose = null
        },
        selectedMinutes = selectedMinutes,
        customMinutesText = customMinutes,
        onMinutesSelected = { minutes ->
            selectedMinutes = if (selectedMinutes == minutes) null else minutes
            customMinutes = ""
        },
        onCustomMinutesChanged = { text ->
            customMinutes = text.filter { it.isDigit() }.take(4)
            if (customMinutes.isNotBlank()) selectedMinutes = null
        },
        mathChallengeEnabled = challenge != null,
        mathProblem = challenge?.text.orEmpty(),
        mathAnswer = mathAnswer,
        mathAnswerState = challenge?.check(mathAnswer) ?: MathAnswerState.NEUTRAL,
        onMathAnswerChanged = { text -> mathAnswer = text.filter { it.isDigit() }.take(3) },
        onStart = {
            val minutes = customMinutes.toIntOrNull() ?: selectedMinutes
            val purpose = selectedPurpose ?: customPurpose.takeIf { it.isNotBlank() }
            if (minutes != null && minutes > 0 && purpose != null) {
                onStart(purpose, minutes)
            }
        },
        onSkip = onSkip,
    )
}

@Composable
fun TimeUpOverlay(
    target: OverlayTarget.TimeUp,
    settings: AppSettings,
    onExtend: (reason: String, extraMinutes: Int) -> Unit,
    onFinish: () -> Unit,
) {
    var reason by remember { mutableStateOf<String?>(null) }
    var customReason by remember { mutableStateOf("") }
    var extraMinutes by remember { mutableStateOf(10) }

    TimeUpScreen(
        state = TimeUpUiState(
            appLabel = target.session.appLabel,
            usedMinutes = target.session.totalMinutes,
            reasonTags = settings.purposeTags,
            selectedReason = reason,
            customReason = customReason,
            extraMinutes = extraMinutes,
        ),
        onReasonSelected = { tag -> reason = if (reason == tag) null else tag },
        onCustomReasonChanged = { customReason = it },
        onExtraMinutesChanged = { extraMinutes = it },
        onExtend = {
            val text = reason ?: customReason.takeIf { it.isNotBlank() } ?: return@TimeUpScreen
            onExtend(text, extraMinutes)
        },
        onFinish = onFinish,
    )
}
