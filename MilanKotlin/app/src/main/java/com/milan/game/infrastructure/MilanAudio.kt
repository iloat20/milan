package com.milan.game.infrastructure

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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
    private var bgmPlayer: ExoPlayer? = null
    private var bgmName: String? = null
    /** 最新 BGM 意图（null=停止）；后台线程串行消费，防连续切换重复构建。 */
    private var bgmTarget: String? = null
    /** BGM 构建专用单线程执行器：ExoPlayer 构建/prepare 移出主线程（低端机性能优化）。 */
    private val bgmExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "milan-bgm") }
    private var sfxVolume = 0.9f
    private var bgmVolume = 0.7f

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
    }

    /** 播放短音效（按需懒加载缓存；同名重复播放不重复载入）。 */
    fun playSfx(name: String) {
        val c = appContext ?: return
        val pool = sfxPool ?: return
        val id = sfxIds[name] ?: try {
            c.assets.openFd("audio/sfx/$name.ogg").use { afd ->
                pool.load(afd, 1).also { loaded -> if (loaded > 0) sfxIds[name] = loaded }
            }
        } catch (_: Exception) {
            -1 // 资源缺失：静默
        }
        if (id > 0) pool.play(id, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    /** 播放背景乐（循环）；同名重复调用不重启。构建在后台线程串行执行，不阻塞主线程。 */
    @Synchronized
    fun playBgm(name: String) {
        val c = appContext ?: return
        if (bgmName == name || bgmTarget == name) return
        bgmTarget = name
        bgmExecutor.execute { runBgm(c, name) }
    }

    @Synchronized
    fun stopBgm() {
        bgmTarget = null
        bgmExecutor.execute {
            synchronized(this) {
                bgmPlayer?.release()
                bgmPlayer = null
                bgmName = null
            }
        }
    }

    /** 后台线程执行：按最新意图构建播放器；意图已变则丢弃，不重复构建。 */
    private fun runBgm(c: Context, name: String) {
        synchronized(this) {
            if (bgmTarget != name) return
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
            val player = ExoPlayer.Builder(c).build().apply {
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
        }
    }

    fun setSfxVolume(v: Float) { sfxVolume = v.coerceIn(0f, 1f) }
    fun setBgmVolume(v: Float) { bgmVolume = v.coerceIn(0f, 1f) }

    /** 释放全部音频资源。 */
    fun release() {
        stopBgm()
        bgmExecutor.shutdown()
        sfxPool?.release()
        sfxPool = null
        sfxIds.clear()
        appContext = null
    }
}
