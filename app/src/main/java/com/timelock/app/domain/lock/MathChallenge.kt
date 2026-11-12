package com.timelock.app.domain.lock

import kotlin.random.Random

/**
 * 一道口算题。
 *
 * 难度目标只有一句：**能靠心算做出来，但不能靠本能答出来。**
 *
 * 为此下面两类题一道都不出：
 * - **九九表能直接对上的**：`7 × 3`、`64 ÷ 8`（表反查）、`8 + 5`；
 * - **表 + 补个零的伪装**：`3 × 20`、`90 ÷ 3`、`90 − 70`、`73 + 20`——本质还是一位数运算。
 *
 * 具体契约（[MathChallengeGenerator] 靠它筛出整个题库）：
 * 1. **全部整数**，所有操作数与答案都不超过 100，除法必须整除；
 * 2. **操作数与答案都 ≥ 2**（排除 `x ÷ x`、`x ÷ 1`、`x × 1`、`x − x`、`x ± 1`）；
 * 3. **每道题至少有一边是两位数（≥ 11）**——所以不可能是九九表里的一条；
 * 4. **不存在"整十"的偷懒路径**——乘除里不许出现 10 的倍数，
 *    加减里两边都不许是整十。
 */
data class MathChallenge(
    val a: Int,
    val op: String,
    val b: Int,
    val answer: Int,
) {
    /** 题面，如 `79 ÷ 79`（不含等号）。 */
    val text: String get() = "$a $op $b"
}

/** 计算验证码的判定结果。 */
enum class MathAnswerState {
    /** 还没填，或者填的还是正确答案的前缀（例如正确答案 95，已输入 9）。 */
    NEUTRAL,
    CORRECT,
    WRONG,
}

object MathChallengeGenerator {

    /** 一位数一侧（乘除法专用）的范围。 */
    private const val ONE_DIGIT_MIN = 2
    private const val ONE_DIGIT_MAX = 9

    /**
     * 「两位数」一侧的下限。取 11 而不是 10：`×10` / `÷ 得 10` 这种添个零就完事的题，
     * 跟九九表一样属于本能反应，也要排除。
     */
    private const val TWO_DIGIT_MIN = 11

    /** 百以内。 */
    private const val LIMIT = 100

    /**
     * 四种运算各自的题库，**启动时枚举一次、之后只做随机取**。
     *
     * 为什么不就地随机生成：加法/减法可选范围大，乘除法因为积、商不超 100，
     * 可选范围很小（比如 9 的倍数只剩 `9 × 11` 一条）。就地随机会让这些"独苗题"
     * 反复出现，用户做几次就把它背下来了——那又变成本能反应。
     * 枚举全量之后每道题等概率，谁都不会特别脸熟。
     */
    private val POOLS: List<List<MathChallenge>> by lazy {
        listOf(
            buildPool { add ->
                for (a in TWO_DIGIT_MIN..LIMIT - TWO_DIGIT_MIN) {
                    for (b in TWO_DIGIT_MIN..LIMIT - a) add(MathChallenge(a, "+", b, a + b))
                }
            },
            buildPool { add ->
                for (a in TWO_DIGIT_MIN + 2..LIMIT) {
                    for (b in TWO_DIGIT_MIN..a - 2) add(MathChallenge(a, "-", b, a - b))
                }
            },
            buildPool { add ->
                for (f in ONE_DIGIT_MIN..ONE_DIGIT_MAX) {
                    for (g in TWO_DIGIT_MIN..LIMIT / f) add(MathChallenge(f, "×", g, f * g))
                }
            },
            buildPool { add ->
                for (d in ONE_DIGIT_MIN..ONE_DIGIT_MAX) {
                    for (q in TWO_DIGIT_MIN..LIMIT / d) add(MathChallenge(a = d * q, op = "÷", b = d, answer = q))
                }
            },
        )
    }

    /**
     * 先按区间铺出候选，再用契约筛一遍。
     *
     * 区间只负责"形状"（位数、范围、整除），难度规则全部集中在 [satisfiesContract]，
     * 这样规则只有一处、改起来不会漏。
     */
    private inline fun buildPool(generate: (add: (MathChallenge) -> Unit) -> Unit): List<MathChallenge> {
        val result = ArrayList<MathChallenge>()
        generate { candidate -> if (candidate.satisfiesContract()) result.add(candidate) }
        return result
    }

    fun generate(random: Random = Random.Default): MathChallenge {
        val pool = POOLS[random.nextInt(POOLS.size)]
        return pool[random.nextInt(pool.size)]
    }
}

/**
 * 逐条核对 [MathChallenge] 文档里写的契约。
 *
 * 题库就是靠它筛出来的，所以这里只要写得对，题库里就不可能出现"靠本能答"的题。
 */
private fun MathChallenge.satisfiesContract(): Boolean {
    if (answer < 2 || answer > 100) return false

    val shapedOk = when (op) {
        "+" -> a in 11..89 && b in 11..100 && a + b == answer && answer <= 100
        "-" -> a in 13..100 && b in 11..100 && b <= a - 2 && a - b == answer
        "×" -> a in 2..9 && b in 11..100 && a * b == answer && answer <= 100
        "÷" -> b in 2..9 && answer in 11..100 && a <= 100 && a % b == 0 && a / b == answer
        else -> return false
    }
    return shapedOk && !hasRoteShortcut()
}

/**
 * 是否存在"不用真算"的捷径。
 *
 * 这几类都是**九九表换个皮**：
 * - `3 × 20`、`90 ÷ 3`：算一位数再补个零；
 * - `73 + 20`、`90 − 70`：加减里只要有一边是整十，就退化成"动一下十位"，一眼就出；
 * - `4 × 11 = 44` / `99 ÷ 9 = 11`：11 的乘除就是"个位重复一遍""倒着背"，同样是本能；
 * - `75 − 73 = 2`：两边十位相同，实际只算了个位数。
 */
private fun MathChallenge.hasRoteShortcut(): Boolean = when (op) {
    "×" -> a % 10 == 0 || b % 10 == 0 || b == 11
    "÷" -> a % 10 == 0 || answer % 10 == 0 || answer == 11
    "+" -> a % 10 == 0 || b % 10 == 0
    "-" -> a % 10 == 0 || b % 10 == 0 || a / 10 == b / 10
    else -> true
}

/**
 * 即时判定，不用等用户按确认。
 *
 * 关键的一条：**还在往正确答案上打字时不报错**。
 * 正确答案 95、用户刚敲下 9 的那一刻，不该立刻标红说"答案不对"——
 * 那会让每一次输入都像犯错。只有当前输入已经不可能长成正确答案时才判错。
 */
fun MathChallenge.check(input: String): MathAnswerState = when {
    input.isBlank() -> MathAnswerState.NEUTRAL
    input.toIntOrNull() == answer -> MathAnswerState.CORRECT
    answer.toString().startsWith(input) -> MathAnswerState.NEUTRAL
    else -> MathAnswerState.WRONG
}
