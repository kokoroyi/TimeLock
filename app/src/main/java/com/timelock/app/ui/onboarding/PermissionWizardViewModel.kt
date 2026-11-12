package com.timelock.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.timelock.app.system.DeviceBrand
import com.timelock.app.system.PermissionChecker
import com.timelock.app.system.PermissionKind
import com.timelock.app.system.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionWizardViewModel(
    private val checker: PermissionChecker,
) : ViewModel() {

    private val _statuses = MutableStateFlow(checker.snapshot())
    val statuses: StateFlow<List<PermissionStatus>> = _statuses.asStateFlow()

    val brand: DeviceBrand = DeviceBrand.detect()

    /**
     * 每次回到前台都必须重新实测——用户可能刚从系统设置里改了权限。
     * 这是外链教程永远做不到的事：它无法自检。
     */
    fun refresh() {
        _statuses.value = checker.snapshot()
    }

    fun allGranted(): Boolean = _statuses.value.all { it.granted }

    fun settingsIntent(kind: PermissionKind): Intent? = checker.settingsIntent(kind)

    /**
     * 跳到本应用的「应用信息」页。
     *
     * 实测小米 HyperOS 上，这一个页面里同时有「省电策略」「自启动」「正在运行」三项，
     * 正好覆盖后台不被限制所需的全部设置——比逐个去找入口可靠得多。
     */
    fun appDetailsIntent(): Intent {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", checker.packageName, null),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
