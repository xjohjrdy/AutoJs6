package org.autojs.autojs.ui.floating

import android.app.Activity
import android.os.Bundle
import android.util.Log
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission

/**
 * Shows the circular (floating) menu every time the home screen comes up, so the automation tools
 * are one tap away without the user flipping the drawer switch first. Called by the home screen's
 * `onCreate` (cold start) and `onNewIntent` (a launcher tap that reuses the `singleTask` instance).
 *
 * The policy is deliberately conservative about the three things that would make an automatic show
 * harmful:
 *
 * - `savedInstanceState != null` means the screen is being recreated (a theme change), not started;
 *   the menu already survives that and must not be rebuilt.
 * - The overlay permission is required. Without it [FloatyWindowManger.showCircularMenu] would toast
 *   an error and open the system settings page on every launch, so the attempt is skipped instead.
 * - Nothing happens while the menu is already showing, so repeated triggers are free.
 *
 * zh-CN: 每次进入首页时显示悬浮菜单, 用户无需先打开抽屉开关. 由首页的 `onCreate` (冷启动) 与
 * `onNewIntent` (复用 `singleTask` 实例的桌面图标点击) 调用.
 *
 * 策略对三种 "自动显示反而有害" 的情况保持保守:
 *
 * - `savedInstanceState != null` 表示页面正在重建 (切换主题) 而非启动; 菜单本身会存活, 不应重建.
 * - 必须有 "显示在其他应用上层" 权限. 缺少时 [FloatyWindowManger.showCircularMenu] 会在每次启动弹出
 *   错误 toast 并打开系统设置页, 因此这里直接跳过.
 * - 菜单已在显示时不做任何事, 因此重复触发没有代价.
 */
object FloatyStartupShow {

    private const val TAG = "FloatyStartupShow"

    fun onHomeShown(activity: Activity, savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        if (activity.isFinishing || activity.isDestroyed) return
        if (FloatyWindowManger.isCircularMenuShowing()) return
        if (!canDrawOverlays(activity)) {
            Log.d(TAG, "Floating menu not shown: the overlay permission is not granted.")
            return
        }
        val decorView = activity.window?.decorView ?: return
        // Deferred so the window is added after the home screen has been laid out.
        // zh-CN: 延迟执行, 使窗口在首页完成布局后再添加.
        decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (FloatyWindowManger.isCircularMenuShowing()) return@post
            runCatching { FloatyWindowManger.showCircularMenuIfNeeded(activity) }
                .onFailure { Log.w(TAG, "Unable to show the floating menu automatically.", it) }
            if (FloatyWindowManger.isCircularMenuShowing()) {
                // Keep the drawer switch in step with what is actually on screen.
                // zh-CN: 让抽屉开关与实际显示状态保持一致.
                MiscStateKeys.FLOATING_MENU_SHOWN.set(true)
                Log.d(TAG, "Floating menu shown automatically on app start.")
            }
        }
    }

    // Some OEMs throw from the settings lookup instead of returning false; treat that as "no".
    // zh-CN: 部分定制系统在查询设置时抛异常而非返回 false, 一并视为 "无权限".
    private fun canDrawOverlays(activity: Activity): Boolean =
        runCatching { DisplayOverOtherAppsPermission(activity).has() }.getOrDefault(false)

}
