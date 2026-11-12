package com.timelock.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.timelock.app.BuildConfig
import com.timelock.app.ui.LocalAppContainer
import com.timelock.app.ui.apps.PickerMode
import com.timelock.app.ui.apps.SelectAppsScreen
import com.timelock.app.ui.history.HistoryScreen
import com.timelock.app.ui.home.HomeScreen
import com.timelock.app.ui.lock.LockPromptPreviewScreen
import com.timelock.app.ui.onboarding.PermissionWizardScreen
import com.timelock.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SELECT_APPS = "select_apps"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val WHITELIST = "whitelist"

    /** 仅调试版：预览拦截界面改版。发布版不注册。 */
    const val PREVIEW_LOCK_PROMPT = "preview_lock_prompt"
    const val PREVIEW_LOCK_PROMPT_NO_TAGS = "preview_lock_prompt_no_tags"
    const val PREVIEW_LOCK_PROMPT_MATH = "preview_lock_prompt_math"
}

@Composable
fun TimeLockNavHost(startDestination: String) {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            PermissionWizardScreen(
                onAllGranted = {
                    scope.launch {
                        container.settingsStore.updateSettings { it.copy(onboardingCompleted = true) }
                    }
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onAddApp = { navController.navigate(Routes.SELECT_APPS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SELECT_APPS) {
            SelectAppsScreen(
                mode = PickerMode.MANAGED,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.WHITELIST) {
            SelectAppsScreen(
                mode = PickerMode.WHITELIST,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenWhitelist = { navController.navigate(Routes.WHITELIST) },
            )
        }

        // 只存在于调试版：评审拦截界面改版效果用，发布版里不会注册。
        if (BuildConfig.DEBUG) {
            composable(Routes.PREVIEW_LOCK_PROMPT) {
                LockPromptPreviewScreen()
            }
            composable(Routes.PREVIEW_LOCK_PROMPT_NO_TAGS) {
                LockPromptPreviewScreen(emptyTags = true)
            }
            composable(Routes.PREVIEW_LOCK_PROMPT_MATH) {
                LockPromptPreviewScreen(mathChallenge = true)
            }
        }
    }
}
