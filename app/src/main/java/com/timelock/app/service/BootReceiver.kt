package com.timelock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timelock.app.TimeLockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新后把看护服务拉起来。
 *
 * 没有它会出现最糟糕的那种"锁不住"：用户重启手机后以为还在管着，
 * 实际上服务根本没跑，而应用界面此时看不出任何异常。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContext = context.applicationContext
        val application = appContext as? TimeLockApplication ?: return
        val container = application.container

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = container.settingsStore.settings.first()
                val permissionsOk = container.permissionChecker.allGranted()
                if (settings.onboardingCompleted && permissionsOk) {
                    LockMonitorService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
