package com.timelock.app.system

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 本应用运转所需的四项系统授权。
 *
 * 顺序即引导顺序：先给「使用情况访问」（能读到别的 App 才有意义），
 * 再给「悬浮窗」（锁屏界面要盖在别人上面），然后是通知与电池白名单（保证不被杀）。
 */
enum class PermissionKind {
    USAGE_ACCESS,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
}

data class PermissionStatus(
    val kind: PermissionKind,
    val granted: Boolean,
)

/**
 * 权限状态的唯一判定处。
 *
 * 关键点：**所有判断都必须是"当场实测"的**——用户在系统设置里改了权限，
 * 回到应用就要立刻反映出来。把说明写在外部网页上的做法既不能自检，也不离线，
 * 这里全部本地化。
 */
class PermissionChecker(private val context: Context) {

    /** 本应用包名。用于拼「跳到应用信息页」的 intent。 */
    val packageName: String = context.packageName

    fun isGranted(kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.USAGE_ACCESS -> hasUsageAccess()
        PermissionKind.OVERLAY -> Settings.canDrawOverlays(context)
        PermissionKind.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        PermissionKind.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations()
    }

    fun snapshot(): List<PermissionStatus> =
        PermissionKind.entries.map { PermissionStatus(it, isGranted(it)) }

    fun allGranted(): Boolean = PermissionKind.entries.all { isGranted(it) }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 打开该项权限对应的系统设置页。返回 null 表示该机型没有对应页面。 */
    fun settingsIntent(kind: PermissionKind): Intent? {
        val pkgUri = Uri.fromParts("package", context.packageName, null)
        return when (kind) {
            PermissionKind.USAGE_ACCESS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)

            PermissionKind.OVERLAY ->
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)

            PermissionKind.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

            PermissionKind.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
        }.takeIf { it.resolveActivity(context.packageManager) != null }
            ?: fallbackIntent(kind)
    }

    private fun fallbackIntent(kind: PermissionKind): Intent? = when (kind) {
        PermissionKind.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        PermissionKind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        PermissionKind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        PermissionKind.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }.takeIf { it.resolveActivity(context.packageManager) != null }
}
