package io.github.ffelixq.medswidget.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager

object SensitiveWindowProtection {
    fun apply(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.window.setHideOverlayWindows(true)
        }
        activity.window.decorView.filterTouchesWhenObscured = true
    }
}
