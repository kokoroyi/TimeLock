package com.timelock.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 用悬浮窗承载拦截界面。
 *
 * 为什么不用 Activity 盖上去：Android 10 起对"后台启动 Activity"限制越来越严，
 * 国产 ROM 更是层层加码。而 `TYPE_APPLICATION_OVERLAY` 只需要用户授过一次悬浮窗权限，
 * 就稳定可靠——这不是取巧，是这个场景下唯一站得住的方案。
 */
class LockOverlayController(private val context: Context) {

    private val windowManager: WindowManager? =
        context.getSystemService(WindowManager::class.java)

    private var composeView: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    /**
     * 只能以"视图真的还挂在窗口上"为准，不能只看自己缓存的引用。
     *
     * 踩过的坑：**系统撤销悬浮窗权限时会把我们的窗口直接摘掉**，而 `composeView`
     * 这个引用还在。于是 `isShowing` 永远为真 → `showIfChanged` 每次都提前返回 →
     * 浮层再也不出现，而且是完全静默的。用户看到的是"权限正常、服务在跑、就是不拦"。
     */
    val isShowing: Boolean get() = composeView?.parent != null

    fun show(content: @Composable () -> Unit) {
        if (isShowing) return

        // 视图已被系统摘掉（权限被撤销、被 ROM 清理）：先清干净残留状态再重建，
        // 否则 ComposeView 会带着已经 DESTROYED 的生命周期owner复活。
        if (composeView != null) hide()

        val wm = windowManager
        if (wm == null) {
            Log.w(TAG, "没有 WindowManager，悬浮窗无法显示")
            return
        }

        val lifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent(content)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 刻意不加 FLAG_LAYOUT_IN_SCREEN：加上会把状态栏一起盖住，
            // 结果是系统仍按深色背景画白色状态栏文字，落在浅色界面上几乎看不见。
            // 交给系统做 inset，界面自然落在状态栏与导航栏之间。
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )

        // 注意不能写成 runCatching { windowManager?.addView(...) }.isSuccess——
        // 那样 windowManager 为 null 时 lambda 返回 null，isSuccess 仍是 true，
        // 会把"根本没加上"误判成成功。
        val failure = runCatching { wm.addView(view, params) }.exceptionOrNull()
        if (failure != null) {
            Log.w(TAG, "悬浮窗添加失败（多半是悬浮窗权限被回收）", failure)
            lifecycleOwner.onDestroy()
            return
        }
        composeView = view
        owner = lifecycleOwner
    }

    fun hide() {
        val view = composeView ?: return
        composeView = null
        runCatching { windowManager?.removeViewImmediate(view) }
        owner?.onDestroy()
        owner = null
    }

    private companion object {
        const val TAG = "LockOverlay"
    }
}

/**
 * ComposeView 挂到 WindowManager 上时没有宿主生命周期，必须自带一套。
 * 少了它，Compose 会在第一帧就抛 "ViewTreeLifecycleOwner not found"。
 */
private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
