package com.milan.game.infrastructure

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.milan.game.ui.GameState
import kotlinx.coroutines.flow.first

/**
 * 每日补给检查任务：未领今日免费补给才发通知。
 *
 * 冷启动场景：进程可能被 WorkManager 单独拉起，此时 Application 的异步初始化
 * 未必完成——先挂起等待 [GameState.ready] 再触存档（StateFlow 语义保证可见 serviceRef）。
 * 全路径不抛异常：后台任务失败不应惊扰用户，重试交由下一个周期。
 */
class DailySupplyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            GameState.ready.first { it }
            val service = GameState.service
            // 今日已领过任意档每日特惠 → 不打扰（领取判定与商店页同源）
            if (service.dailyBoughtToday().isNotEmpty()) return Result.success()

            val context = applicationContext
            if (!DailySupplyNotifier.canNotify(context)) return Result.success()
            val notification = NotificationCompat.Builder(context, DailySupplyNotifier.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("每日补给待领取")
                .setContentText("今日的免费星尘与战票已经刷新，回城领取 ✦")
                .setAutoCancel(true)
                .build()
            // canNotify 已校验权限；SecurityException 兜底静默（权限可能在检查后被撤销）
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification) }
            Result.success()
        } catch (_: Exception) {
            // 初始化失败等异常：本周期放弃，下个周期自然重试（不返回 retry 避免指数退避堆积日志）
            Result.success()
        }
    }

    private companion object {
        const val NOTIFY_ID = 1001
    }
}
