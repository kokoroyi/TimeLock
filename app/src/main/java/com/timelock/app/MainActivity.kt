package com.timelock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.timelock.app.domain.model.AppSettings
import com.timelock.app.domain.model.ThemeMode
import com.timelock.app.service.LockMonitorService
import com.timelock.app.ui.LocalAppContainer
import com.timelock.app.ui.navigation.Routes
import com.timelock.app.ui.navigation.TimeLockNavHost
import com.timelock.app.ui.theme.TimeLockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as TimeLockApplication).container

        // 仅调试版可用：直接进某个界面看效果（例如预览拦截界面）。
        // 发布版里这段是死代码，不会被任何外部 intent 触发。
        val previewScreen = if (BuildConfig.DEBUG) {
            intent?.getStringExtra(EXTRA_PREVIEW_SCREEN)
        } else {
            null
        }

        setContent {
            TimeLockRoot(container = container, previewScreen = previewScreen)
        }
    }

    private companion object {
        const val EXTRA_PREVIEW_SCREEN = "preview_screen"
    }
}

@Composable
private fun TimeLockRoot(
    container: com.timelock.app.di.AppContainer,
    previewScreen: String? = null,
) {
    var settings by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(container) {
        container.settingsStore.settings.collect { settings = it }
    }

    val current = settings
    if (current == null) {
        // 设置尚未读出，先留白，避免闪到错误的首屏
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val darkTheme = when (current.themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    TimeLockTheme(darkTheme = darkTheme, dynamicColor = current.dynamicColor) {
        CompositionLocalProvider(LocalAppContainer provides container) {
            val context = LocalContext.current

            // 权限状态必须是"会变的"。
            // 之前写成一次性求值，导致用户去系统设置里开完权限、返回应用后，
            // 这里仍然拿着旧的 false，于是服务不会被启动、拦截直接失效——
            // 真机上表现为"授权了却拦不住"，重启应用才恢复。
            var permissionsOk by remember { mutableStateOf(container.permissionChecker.allGranted()) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                permissionsOk = container.permissionChecker.allGranted()
            }

            LaunchedEffect(permissionsOk) {
                if (permissionsOk) {
                    LockMonitorService.start(context)
                } else {
                    LockMonitorService.stop(context)
                }
            }

            val start = previewScreen ?: if (current.onboardingCompleted && permissionsOk) {
                Routes.HOME
            } else {
                Routes.ONBOARDING
            }
            TimeLockNavHost(startDestination = start)
        }
    }
}
