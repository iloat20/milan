package com.milan.game.infrastructure

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量音频服务（单例）。
 *
 * 资源约定：assets/audio/sfx/<名称>.ogg（短音效，SoundPool 池化）与
 * assets/audio/bgm/<名称>.ogg（背景乐，ExoPlayer 循环）。
 * 全部调用容错：资源缺失 / 未初始化时静默跳过，游戏可无音效正常运行。
 * 生命周期：MilanApp.onCreate 调 [init]；进程结束由系统回收（[release] 亦可用）。
 */
object MilanAudio {

    private var appContext: Context? = null
    private var sfxPool: SoundPool? = null
    private val sfxIds = ConcurrentHashMap<String, Int>()
    /** sampleId → 音效名：SoundPool.load 完成前的待播意图（load 异步，完成回调据此补放首次触发）。 */
    private val loadingSfx = ConcurrentHashMap<Int, String>()
    private var bgmPlayer: ExoPlayer? = null
    private var bgmName: String? = null
    /** 最新 BGM 意图（null=停止）；BGM looper 线程串行消费，防连续切换重复构建。 */
    private var bgmTarget: String? = null
    /**
     * BGM 专用 HandlerThread（坑因 2026-08-08：曾用无 Looper 的 Executor 构建/操作 ExoPlayer，
     * 导致「Player is accessed on the wrong thread」崩溃——media3 要求 Player 的所有方法
     * （build/setMediaItem/prepare/play/release）都在其 application looper 线程调用）。
     * 本线程即 Player 的 looper：所有操作经 bgmHandler.post 在此串行执行，
     * 既满足线程约束，又把耗时构建移出主线程（低端机性能优化）。
     */
    private val bgmThread = HandlerThread("milan-bgm").apply { start() }
    private val bgmHandler = Handler(bgmThread.looper)
    private var sfxVolume = 0.9f
    private var bgmVolume = 0.7f

    // ── AudioFocus（P3-9）：来电/语音导航/其他 App 播放时按系统焦点指令 duck 或暂停，
    // 此前只处理了 Activity 前后台，外部抢占焦点时 BGM 会继续响。
    private var focusRequest: AudioFocusRequest? = null

    private val audioManager: AudioManager?
        get() = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** 请求独占焦点（BGM looper 线程内调用；回调同样切回该 looper，满足 media3 线程约束）。 */
    private fun requestFocus() {
        val am = audioManager ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::onFocusChange, bgmHandler)
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        val req = focusRequest ?: return
        am.abandonAudioFocusRequest(req)
        focusRequest = null
    }

    /** 焦点变化回调（在 BGM looper 线程执行）。 */
    private fun onFocusChange(focus: Int) {
        bgmHandler.post {
            synchronized(this) {
                when (focus) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        // 永久失去：停播并清意图（不释放播放器，回前台可恢复）
                        bgmPlayer?.pause()
                        bgmName = null
                        bgmTarget = null
                        abandonFocus()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        bgmPlayer?.pause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        bgmPlayer?.volume = bgmVolume * 0.2f
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        bgmPlayer?.volume = bgmVolume
                        bgmPlayer?.play()
                    }
                }
            }
        }
    }

    /** 初始化（幂等）。@param context 任意 Context，内部转 applicationContext。 */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        sfxPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
            .apply {
                // 坑因（2026-08-10）：SoundPool.load 是异步的——首次 playSfx 时 load 只排队，
                // 紧随其后的 pool.play 会因样本未加载完成而静默失败（返回 0），表现为
                // 「每次冷启动后每个音效首次触发无声」。这里在加载完成回调里补放一次。
                // 回调运行在 SoundPool 内部线程，loadingSfx 用 ConcurrentHashMap 保证线程安全。
                setOnLoadCompleteListener { pool, sampleId, status ->
                    val pendingName = loadingSfx.remove(sampleId) ?: return@setOnLoadCompleteListener
                    if (status == 0 && sfxIds[pendingName] == sampleId) {
                        pool.play(sampleId, sfxVolume, sfxVolume, 1, 0, 1f)
                    }
                }
            }
    }

    /** 播放短音效（按需懒加载缓存；同名重复播放不重复载入）。 */
    fun playSfx(name: String) {
        val c = appContext ?: return
        val pool = sfxPool ?: return
        // 加载失败（资源缺失/损坏）缓存 -1 哨兵：此前失败不落表，同名缺失音效每次触发
        // 都重新 openFd + 抛异常（按钮高频音效路径上反复 IO）。哨兵随 release() 一并清空。
        val id = sfxIds[name] ?: run {
            val loaded = try {
                c.assets.openFd("audio/sfx/$name.ogg").use { afd ->
                    pool.load(afd, 1)
                }
            } catch (_: Exception) {
                -1 // 资源缺失：静默
            }
            if (loaded > 0) {
                sfxIds[name] = loaded
                loadingSfx[loaded] = name // 加载完成回调据此补放首次触发
            } else {
                sfxIds[name] = -1
            }
            loaded
        }
        if (id > 0) pool.play(id, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    /** 播放背景乐（循环）；同名重复调用不重启。实际执行在 BGM looper 线程，不阻塞主线程。 */
    @Synchronized
    fun playBgm(name: String) {
        val c = appContext ?: return
        // 防重入：仅当「无待处理意图且正在播同名」或「待处理意图即同名」时跳过。
        // 坑因：bgmName 记录的是实际在播曲目；若只判 bgmName==name，在「播放 A → 请求 B（未执行）→ 请求 A」时，
        // bgmName 仍残留 A，会把最后一次请求 A 误判为重复而丢弃，最终播放 B 而非用户最后请求的 A。
        if (bgmTarget == name || (bgmTarget == null && bgmName == name)) return
        bgmTarget = name
        bgmHandler.post { runBgm(c, name) }
    }

    @Synchronized
    fun stopBgm() {
        bgmTarget = null
        bgmHandler.post {
            synchronized(this) {
                abandonFocus()
                bgmPlayer?.release()
                bgmPlayer = null
                bgmName = null
            }
        }
    }

    /**
     * App 退后台：停止 BGM 并释放 ExoPlayer（省内存，解码器/缓冲/音轨全部归还）。
     * 与 [stopBgm] 的区别：保留 bgmTarget 意图，回前台由 [resumeForeground] 自动恢复。
     * 不 quit 本线程（重建播放器需复用其 looper）；线程本身开销极小，可忽略。
     */
    @Synchronized
    fun pauseBackground() {
        bgmHandler.post {
            synchronized(this) {
                abandonFocus()
                bgmPlayer?.release()
                bgmPlayer = null
                bgmName = null
            }
        }
    }

    /** App 回前台：若仍有 BGM 意图则恢复播放。去重/存活判断在 looper 线程的 [runBgm] 内做，
     *  避免主线程读 bgmName 误判（如「退后台→立即回前台」时释放任务尚未执行，读到的还是旧值）。 */
    @Synchronized
    fun resumeForeground() {
        val name = bgmTarget ?: return
        val c = appContext ?: return
        bgmHandler.post { runBgm(c, name) }
    }

    /**
     * BGM looper 线程执行：按最新意图构建播放器；意图已变则丢弃，不重复构建。
     * 所有 Player 方法都运行在 bgmThread（构建时显式 setLooper 绑定），满足 media3 线程约束。
     */
    private fun runBgm(c: Context, name: String) {
        synchronized(this) {
            if (bgmTarget != name) return
            // 已在该曲目且播放器存活（如启动时 playBgm 与 resumeForeground 的任务先后执行）→ 不重复构建
            if (bgmName == name) return
            // 资源缺失检查：静默跳过，不创建空播放器
            try {
                c.assets.open("audio/bgm/$name.ogg").close()
            } catch (_: Exception) {
                bgmTarget = null
                return
            }
            // 释放旧播放器（统一在此处，不再依赖 playBgm 先调 stopBgm）
            bgmPlayer?.release()
            bgmPlayer = null
            bgmName = null
            try {
                val player = ExoPlayer.Builder(c)
                    .setLooper(bgmThread.looper)
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri("asset:///audio/bgm/$name.ogg"))
                        repeatMode = Player.REPEAT_MODE_ALL
                        volume = bgmVolume
                        prepare()
                        play()
                    }
                if (bgmTarget != name) { // 构建期间意图已变：释放刚建的，避免泄漏
                    player.release()
                    return
                }
                bgmPlayer = player
                bgmName = name
                requestFocus() // P3-9：开始播放即请求独占焦点
            } catch (_: Exception) {
                // 播放器构建/准备失败：静默放弃本轮，保留「无音效也能玩」的容错约定
                bgmTarget = null
            }
        }
    }

    fun setSfxVolume(v: Float) { sfxVolume = v.coerceIn(0f, 1f) }
    fun setBgmVolume(v: Float) { bgmVolume = v.coerceIn(0f, 1f) }

    /** 释放全部音频资源。quitSafely 会先执行完已入队的 BGM 释放任务再退出线程。 */
    fun release() {
        stopBgm()
        bgmThread.quitSafely()
        sfxPool?.release()
        sfxPool = null
        sfxIds.clear()
        loadingSfx.clear() // 防 sampleId 复用：旧待播意图不带到下次 init
        appContext = null
    }
}
