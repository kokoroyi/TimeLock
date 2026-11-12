package com.timelock.app.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.timelock.app.domain.lock.MathAnswerState
import com.timelock.app.domain.lock.MathChallengeGenerator
import com.timelock.app.domain.lock.check
import com.timelock.app.domain.model.DEFAULT_PURPOSE_TAGS

/**
 * 拦截界面的调试预览宿主（仅 debug 版注册）。
 *
 * 只做一件事：把交互状态接起来，好让人真的点一遍看效果。
 * **不会开始会话、不会落库、不影响看护服务。**
 * 真实的接线在 service/LockOverlayContent.kt 里。
 *
 * @param emptyTags     模拟"用户把所有标签都删光了"：第一段不再出胶囊，直接给输入框。
 * @param mathChallenge 模拟设置里打开了「计算验证码」：会出现第三段。
 */
@Composable
fun LockPromptPreviewScreen(
    emptyTags: Boolean = false,
    mathChallenge: Boolean = false,
) {
    var selectedPurpose by remember { mutableStateOf<String?>(null) }
    var customPurpose by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableStateOf<Int?>(null) }
    var customMinutes by remember { mutableStateOf("") }
    var mathAnswer by remember { mutableStateOf("") }

    val challenge = remember(mathChallenge) {
        if (mathChallenge) MathChallengeGenerator.generate() else null
    }

    LockPromptScreen(
        appLabel = "QQ",
        purposeTags = if (emptyTags) emptyList() else DEFAULT_PURPOSE_TAGS,
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
        onStart = { },
        onSkip = { },
    )
}
