package com.milan.game.ui.gacha

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.toArgb
import com.milan.game.services.PullResult
import com.milan.game.ui.theme.AppTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 抽卡结果分享图（2026-08 三期新增；对标 wish-simulator 类项目的「结果导出图片」功能）。
 *
 * 实现取向：程序化 Canvas 绘制位图而非截屏——不依赖视图层级/硬件加速状态，
 * 结果完全确定（同批次产出同图），也避免把 Snackbar/系统栏截进图里。
 * 分享经 FileProvider 授权 cacheDir/share 只读 URI + 系统 CHOOSER；
 * 失败静默（无接收方/存储异常不影响游戏流程）。
 */
object PullShareCard {

    private const val WIDTH = 1080
    private const val PAD = 48
    private const val CELL_GAP = 16

    /**
     * 绘制并拉起分享。results 为空时直接忽略（调用方已在 UI 层保证非空，双保险）。
     *
     * U1（2026-08-28 审查修复）：**suspend**，绘制 / PNG 压缩 / 写盘全部移出主线程。
     * 1080×~670 位图的 Canvas 绘制 + `compress(PNG,100)` 在低端机上可达数百毫秒，
     * 原实现在 `.clickable {}` 里同步执行，存在明确 ANR 风险。
     * `startActivity` 留在调用方线程（UI 线程）执行，避免跨线程启动 Activity。
     */
    suspend fun shareResults(context: Context, results: List<PullResult>) {
        if (results.isEmpty()) return
        val uri = withContext(Dispatchers.IO) {
            val bitmap = drawCard(results) ?: return@withContext null
            try {
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "pull_result.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } finally {
                bitmap.recycle()
            }
        } ?: return
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享抽卡结果"))
        }
        // 分享路径失败静默：目标应用缺失 / IO 异常都不应打断玩家（与 buzz() 同等容错口径）
    }

    /** 程序化绘制结果卡片；任何绘制异常返回 null 由调用方静默跳过。 */
    private fun drawCard(results: List<PullResult>): Bitmap? = try {
        val cols = 5
        val rows = ceil(results.size / cols.toDouble()).toInt()
        val cellW = (WIDTH - PAD * 2 - CELL_GAP * (cols - 1)) / cols
        val cellH = 200
        val height = PAD + 92 /*标题区*/ + rows * (cellH + CELL_GAP) - CELL_GAP + PAD + 64 /*落款区*/

        val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 背景：浓墨底色 + 顶部金箔饰线（水墨国风调性）
        // U-04：全部改走 AppTheme 水墨 token（Canvas 侧取 toArgb）。
        // 原 0xFFF2F2F2 / 0xFF9AA3B5 是冷白冷灰，与宣纸暖调不符，已改为 Text1(宣纸白) / Text2。
        canvas.drawColor(AppTheme.BgDeepest.toArgb())
        val accent = Paint().apply { color = AppTheme.Gold.toArgb() }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 6f, accent)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppTheme.Gold.toArgb(); textSize = 52f; isFakeBoldText = true
        }
        canvas.drawText("丹青寻访 · 抽卡结果", PAD.toFloat(), (PAD + 44).toFloat(), titlePaint)

        val cellFill = Paint().apply { color = AppTheme.BgMid.toArgb() }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppTheme.Text1.toArgb(); textSize = 26f; textAlign = Paint.Align.CENTER
        }
        val rarityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppTheme.Text2.toArgb(); textSize = 30f
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
        val rect = RectF()

        results.forEachIndexed { i, r ->
            val col = i % cols
            val row = i / cols
            val left = (PAD + col * (cellW + CELL_GAP)).toFloat()
            val top = (PAD + 92 + row * (cellH + CELL_GAP)).toFloat()
            rect.set(left, top, left + cellW, top + cellH)

            val rc = AppTheme.rarityColor(r.rarity).toArgb()
            canvas.drawRoundRect(rect, 22f, 22f, cellFill)
            border.color = rc
            canvas.drawRoundRect(rect, 22f, 22f, border)

            rarityPaint.color = rc
            canvas.drawText(AppTheme.rarityName(r.rarity), rect.centerX(), top + 58f, rarityPaint)
            canvas.drawText(ellipsize(r.characterName, namePaint, cellW - 20f), rect.centerX(), top + 150f, namePaint)
        }

        val ssrPlus = results.count { it.rarity >= 3 }
        val footerY = (height - PAD + 14).toFloat()
        canvas.drawText("共 ${results.size} 抽 · SSR+ $ssrPlus ✦ Milan", PAD.toFloat(), footerY, footerPaint)
        bmp
    } catch (_: Exception) {
        null
    }

    /** 手动省略号（避免引 TextUtils 依赖；cell 宽度有限，超宽截断加 …）。 */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = min(text.length, 12)
        while (end > 0 && paint.measureText(text.take(end) + "…") > maxWidth) end--
        return text.take(end) + "…"
    }
}
