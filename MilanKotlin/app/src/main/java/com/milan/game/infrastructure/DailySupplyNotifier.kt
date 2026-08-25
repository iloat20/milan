package com.milan.game.infrastructure

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日补给本地提醒（2026-08 推送实装：设置页「推送」开关从占位转正）。
 *
 * 设计：
 * - 纯本地 WorkManager 周期任务，无服务端推送依赖——离线游戏语义下「推送」即本地定时提醒；
 * - 任务持久化跨进程重启（WorkManager 自带），无需 RECEIVE_BOOT_COMPLETED 手动重排；
 * - 仅在「今日免费补给未领取」时通知（已领不扰民）；通知权限缺失时静默跳过。
 * - Worker 实现见 [DailySupplyWorker]（冷启动需先等 GameState.ready 再触存档）。
 */
object DailySupplyNotifier {

    const val CHANNEL_ID = "daily_supply"

    private const val WORK_NAME = "daily_supply_reminder"

    /** 提醒时刻：每日 12:00（initialDelay 对齐到首个正午，之后每 24h 触发）。 */
    private fun initialDelayMillis(): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    /**
     * 开关切换入口（幂等）：开启=排程唯一周期任务；关闭=取消。
     * KEEP 策略：重复开启保留原计划，不打乱既定提醒节奏。
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        createChannel(context)
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<DailySupplyWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    /** 通知渠道（API 26+ 必需；重复创建为 no-op）。 */
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "每日补给提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "每日免费补给刷新时的本地提醒" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 权限检查（API 33+ 运行时；低版本安装时授权恒真）。 */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
