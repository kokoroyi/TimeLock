package com.timelock.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.timelock.app.di.AppContainer

/** 手工依赖容器的 Compose 入口，避免为此引入 DI 框架。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未提供：请在 MainActivity 中用 CompositionLocalProvider 注入")
}
