package com.milan.game.ui

import com.milan.game.GameState
import com.milan.game.data.SaveProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * 初始化失败态契约测试。
 *
 * R4-04（2026-08-30 审查）：[GameState.ensureInitialized] 抛异常时 `ready` 永不推进，
 * 而 UI 门控（`MainActivity.MilanNavHost`）只有 `!ready → 加载动画` 一个分支，
 * `MilanApp` 的注释却声称「主界面会显示错误态」——该错误态不存在。
 * 结果是玩家永久卡在加载动画，杀进程重进也一样。
 *
 * 契约：
 *   1. 初始化失败 → `failure` 必须非空（UI 据此渲染错误页）；
 *   2. 失败时 `ready` 必须保持 false（不得放行未就绪的 UI）；
 *   3. 异常不得从 ensureInitialized 逃逸（否则会打穿 Application 的启动流程）。
 *
 * 触发方式：让 `onTrace` 回调抛异常——GameService 构造期会经它留痕
 * （如 `content.loaded.from.json`），从而在初始化中途制造一次失败。
 *
 * 单例说明：GameState 是进程级 object。触碰它的测试不止本类（如
 * GachaDeckScreenTest 会注入 testContent；Robolectric 测试类还会经 MilanApp.onCreate
 * 异步注入真实 data.json）——初始化先后不同会让幂等早退，产生顺序耦合。
 * 故本类 @Before 先 [GameState.resetForTest]（2026-09-02 加），保证失败注入前状态干净，
 * 与其它测试类的执行顺序无关。
 */
class GameStateInitFailureTest {

    private val dataJson: String =
        File("src/main/assets/data.json").takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("测试需真实内容文件：app/src/main/assets/data.json")

    private class MemoryProvider : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean {
            stored = json
            return true
        }

        override fun load(): String = stored ?: ""
        override fun delete() {
            stored = null
        }

        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    @Before
    fun resetSingleton() {
        // 清掉可能由其它测试类（或 MilanApp.onCreate 异步注入）留下的已初始化状态，
        // 保证下面的失败注入真实执行——否则 ensureInitialized 幂等早退会让本用例假绿。
        GameState.resetForTest()
    }

    @Test
    fun `初始化抛异常时记录失败态且不发布 ready`() {
        // 若异常逃逸，本用例会直接以该异常失败（契约 3 同时被验证）。
        GameState.ensureInitialized(
            saveProvider = MemoryProvider(),
            contentJson = dataJson,
            onTrace = { error("模拟留痕通道故障") },
        )

        assertNotNull(
            "初始化失败必须记录 failure，否则 UI 只能永远显示加载动画",
            GameState.failure.value,
        )
        assertFalse(
            "初始化失败时 ready 必须保持 false，不得放行未就绪的导航树",
            GameState.ready.value,
        )
    }
}
