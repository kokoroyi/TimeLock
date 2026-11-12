package com.timelock.app.system

import android.os.Build

/**
 * 厂商分支。国产 ROM 的后台管控策略差异极大，而"锁不住"的根因九成在这里，
 * 所以引导必须分品牌——一篇通用网页糊弄不过去。
 */
enum class DeviceBrand(val displayName: String) {
    XIAOMI("小米 / 红米"),
    VIVO("vivo / iQOO"),
    OPPO("OPPO / 一加 / realme"),
    HONOR("荣耀"),
    HUAWEI("华为"),
    OTHER("通用"),
    ;

    companion object {
        fun detect(): DeviceBrand {
            val brand = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
            return when {
                "xiaomi" in brand || "redmi" in brand || "poco" in brand -> XIAOMI
                "vivo" in brand || "iqoo" in brand -> VIVO
                "oppo" in brand || "oneplus" in brand || "realme" in brand -> OPPO
                "honor" in brand -> HONOR
                "huawei" in brand -> HUAWEI
                else -> OTHER
            }
        }
    }
}

/**
 * 离线权限指引。
 *
 * 设计取舍（重要）：
 * 「一键跳转系统设置」是跨品牌**可靠**的主路径，因此在界面上是主按钮；
 * 下面的文字步骤只是**指引**——各厂商菜单层级随版本频繁变动，无法在此逐一实测，
 * 所以文案刻意用"通常位于…"的措辞，不做绝对承诺。
 */
object PermissionGuide {

    /** 每项权限"为什么需要"，一句话说清，用户才愿意给。 */
    fun rationale(kind: PermissionKind): String = when (kind) {
        PermissionKind.USAGE_ACCESS ->
            "用来知道你刚刚打开了哪个应用。没有它，时间锁无法在正确的时候出现。"

        PermissionKind.OVERLAY ->
            "用来把锁屏界面盖在受管应用上面。没有它，时间到了也拦不住。"

        PermissionKind.NOTIFICATIONS ->
            "用来常驻显示正在管理的状态，并在快到点时提醒你一次。"

        PermissionKind.BATTERY_OPTIMIZATION ->
            "让系统不要在后台把监测服务杀掉。这一步不做，前面几项随时可能失效。"
    }

    fun steps(kind: PermissionKind, brand: DeviceBrand): List<String> {
        if (brand == DeviceBrand.OTHER) return genericSteps(kind)
        return brandedSteps(kind, brand)
    }

    /**
     * 「后台不被限制」这一项为什么必须单独存在。
     *
     * 因为**它无法用任何 API 自检**，却是"锁不住"最常见的真凶，而且是**所有安卓机型的通病**——
     * 不是某一家的特例。各家只是入口位置和叫法不同，行为是一样的：
     * 你打开别的应用，系统就把时间锁的进程挂起（或限流），服务还"活着"、通知还在、
     * 前面四项权限全绿，但进程一点 CPU 都拿不到，里面一行代码都跑不了。
     *
     * 实测小米 HyperOS 3.0 的对照数据（别的厂商机制相同，只是没在这台机器上量化）：
     * 前台是桌面时 10 秒 20 次轮询，前台是别的应用时 10 秒 0 次。
     * `deviceidle whitelist` 挡不住它，只有把系统的后台限制真正放开才有效。
     *
     * 所以这一项只能靠**用户手动确认**，不能像前面四项那样自动检测。
     */
    fun backgroundRationale(brand: DeviceBrand): String =
        "这是所有安卓手机的通病，不是某一家的特例：只要你打开别的应用，" +
            "系统就可能把时间锁「冻住」——服务看着还在跑，实际一点事都做不了，拦截完全失效，" +
            "而且不会给你任何提示。必须手动把后台限制放开。" +
            backgroundPathHint(brand)

    private fun backgroundPathHint(brand: DeviceBrand): String = when (brand) {
        DeviceBrand.XIAOMI -> "你这台是小米 / 红米，入口在应用信息页的「省电策略」。"
        DeviceBrand.HUAWEI, DeviceBrand.HONOR -> "你这台是华为 / 荣耀，入口在「应用启动管理」。"
        DeviceBrand.OPPO -> "你这台是 OPPO / 一加 / realme，入口在「耗电管理」。"
        DeviceBrand.VIVO -> "你这台是 vivo / iQOO，入口在「后台耗电管理」。"
        DeviceBrand.OTHER -> "不同机型入口不一样，下面按你的机型给了路径。"
    }

    fun backgroundSteps(brand: DeviceBrand): List<String> = when (brand) {
        DeviceBrand.XIAOMI -> listOf(
            "点上面的「去设置」，会直接打开「时间锁」的应用信息页",
            "点「省电策略」，选「无限制」",
            "回到同一页，把「自启动」也打开",
            "建议顺便在最近任务里给时间锁「加锁」，避免被一键清理掉",
        )

        DeviceBrand.HUAWEI, DeviceBrand.HONOR -> listOf(
            "点上面的「去设置」打开应用信息页",
            "进入「耗电详情」→「应用启动管理」",
            "关闭「自动管理」，然后手动勾选「允许自启动」「允许关联启动」「允许后台活动」",
        )

        DeviceBrand.OPPO -> listOf(
            "点上面的「去设置」打开应用信息页",
            "进入「耗电管理」",
            "打开「允许完全后台行为」与「允许应用自启动」",
        )

        DeviceBrand.VIVO -> listOf(
            "点上面的「去设置」打开应用信息页",
            "进入「电池」→「后台耗电管理」",
            "选择「允许后台高耗电」",
        )

        DeviceBrand.OTHER -> listOf(
            "点上面的「去设置」打开应用信息页",
            "找到「电池」「耗电管理」或「后台限制」一类的入口",
            "把它设为不受限制 / 允许后台活动",
            "如果没有这类选项，就到系统的「电池优化」里把时间锁排除掉",
        )
    }

    private fun genericSteps(kind: PermissionKind): List<String> = when (kind) {
        PermissionKind.USAGE_ACCESS -> listOf(
            "打开「设置」→「应用」→「特殊应用权限」",
            "找到「使用情况访问」或「有权查看使用情况的应用」",
            "在列表里找到「时间锁」并允许",
        )

        PermissionKind.OVERLAY -> listOf(
            "打开「设置」→「应用」→「特殊应用权限」",
            "找到「显示在其他应用上层」或「悬浮窗」",
            "允许「时间锁」",
        )

        PermissionKind.NOTIFICATIONS -> listOf(
            "打开「设置」→「应用」→「时间锁」→「通知」",
            "允许通知",
        )

        PermissionKind.BATTERY_OPTIMIZATION -> listOf(
            "打开「设置」→「应用」→「时间锁」→「电池」",
            "选择「不受限制」或「允许后台活动」",
        )
    }

    private fun brandedSteps(kind: PermissionKind, brand: DeviceBrand): List<String> = when (brand) {
        DeviceBrand.XIAOMI -> when (kind) {
            PermissionKind.USAGE_ACCESS -> listOf(
                "设置 → 隐私保护 → 特殊权限设置 → 使用情况访问",
                "找到「时间锁」并允许",
            )

            PermissionKind.OVERLAY -> listOf(
                "设置 → 应用设置 → 应用管理 → 时间锁 → 权限管理",
                "打开「显示悬浮窗」",
                "顺便把同一页的「后台弹出界面」也打开（HyperOS 特有，缺它锁屏弹不出来）",
            )

            PermissionKind.NOTIFICATIONS -> listOf(
                "设置 → 应用设置 → 应用管理 → 时间锁 → 通知管理",
                "允许通知",
            )

            PermissionKind.BATTERY_OPTIMIZATION -> listOf(
                "设置 → 应用设置 → 应用管理 → 时间锁 → 省电策略",
                "选择「无限制」",
                "再到 设置 → 应用设置 → 权限管理 → 自启动，允许「时间锁」",
            )
        }

        DeviceBrand.VIVO -> when (kind) {
            PermissionKind.USAGE_ACCESS -> listOf(
                "设置 → 更多设置 → 权限管理 → 权限 → 使用情况访问",
                "找到「时间锁」并允许",
            )

            PermissionKind.OVERLAY -> listOf(
                "设置 → 应用与权限 → 权限管理 → 时间锁 → 悬浮窗",
                "允许",
            )

            PermissionKind.NOTIFICATIONS -> listOf(
                "设置 → 应用与权限 → 应用管理 → 时间锁 → 通知",
                "允许通知",
            )

            PermissionKind.BATTERY_OPTIMIZATION -> listOf(
                "设置 → 电池 → 后台高耗电 → 允许「时间锁」",
                "再到 设置 → 应用与权限 → 权限管理 → 自启动，允许「时间锁」",
            )
        }

        DeviceBrand.OPPO -> when (kind) {
            PermissionKind.USAGE_ACCESS -> listOf(
                "设置 → 应用管理 → 应用列表 → 时间锁 → 权限",
                "打开「使用情况」相关权限",
            )

            PermissionKind.OVERLAY -> listOf(
                "设置 → 应用管理 → 应用列表 → 时间锁 → 权限",
                "打开「悬浮窗」",
            )

            PermissionKind.NOTIFICATIONS -> listOf(
                "设置 → 通知与状态栏 → 应用通知 → 时间锁",
                "允许通知",
            )

            PermissionKind.BATTERY_OPTIMIZATION -> listOf(
                "设置 → 电池 → 更多设置 → 睡眠待机优化，关闭它",
                "再到 设置 → 应用管理 → 自启动管理，允许「时间锁」",
            )
        }

        DeviceBrand.HONOR, DeviceBrand.HUAWEI -> when (kind) {
            PermissionKind.USAGE_ACCESS -> listOf(
                "设置 → 隐私 → 权限管理 → 特殊访问权限 → 使用情况访问",
                "找到「时间锁」并允许",
            )

            PermissionKind.OVERLAY -> listOf(
                "设置 → 应用和服务 → 应用管理 → 时间锁 → 权限",
                "打开「显示在其他应用上层」",
            )

            PermissionKind.NOTIFICATIONS -> listOf(
                "设置 → 通知 → 应用通知管理 → 时间锁",
                "允许通知",
            )

            PermissionKind.BATTERY_OPTIMIZATION -> listOf(
                "设置 → 应用和服务 → 应用启动管理 → 时间锁",
                "改为「手动管理」，三项全部允许",
            )
        }

        DeviceBrand.OTHER -> genericSteps(kind)
    }
}
