package com.timelock.app.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /**
     * 应用图标。靠图标认应用比读名字快得多——尤其是名字被翻译得千奇百怪的时候。
     * 取不到时为 null，界面会退化成首字占位。
     */
    val icon: Bitmap?,
)

/** 已安装应用清单。只列出"有启动入口"的应用——没入口的装了也没法打开，不该出现在选择列表里。 */
class InstalledAppCatalog(private val context: Context) {

    suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        resolved.asSequence()
            .mapNotNull { info ->
                val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                val pkg = appInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                InstalledAppInfo(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString().ifBlank { pkg },
                    // 被系统更新覆盖过的也算系统应用，否则同一条会被判成两类
                    isSystem = (appInfo.flags and (
                        ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                        )) != 0,
                    icon = runCatching { info.loadIcon(pm).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX) }
                        .getOrNull(),
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ it.isSystem }, { it.label }))
            .toList()
    }

    fun labelOf(packageName: String): String = runCatching {
        val pm: PackageManager = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * 按包名取图标，供"首页受管列表"这类只知道包名的地方用。
     *
     * 带缓存：首页每次数据变动都会重新取一遍，没有缓存的话会话每跳动一次就要解一遍图。
     * 缓存 null 也是缓存——取不到的包名不该反复重试。
     */
    suspend fun iconOf(packageName: String): Bitmap? {
        synchronized(iconCache) {
            if (iconCache.containsKey(packageName)) return iconCache[packageName]
        }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
            }.getOrNull()
        }
        synchronized(iconCache) { iconCache[packageName] = bitmap }
        return bitmap
    }

    private val iconCache = mutableMapOf<String, Bitmap?>()

    private companion object {
        /** 列表里的头像尺寸，取够大以免在高分屏上发虚。 */
        const val ICON_SIZE_PX = 144
    }
}
