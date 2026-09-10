package com.milan.game.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.DailySupplyNotifier
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.di.AppGraph
import com.milan.game.ui.components.EntranceItem
import com.milan.game.ui.components.GlassDialog
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldSwitch
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ai.CharacterRecommendation
import com.milan.game.ai.GachaRecommendation
import kotlinx.coroutines.launch

/**
 * 设置页（C# SettingsPage 翻译）。
 *
 * 结构：音频与体验（音效/振动/推送/动效减弱）→ 数据管理（重置存档、崩溃日志导出）→ 关于。
 * 写操作与推荐派生经 [SettingsViewModel]（组合根注入）；平台副作用
 * （MilanAudio 音量、WorkManager、通知权限）留在本 Screen 以回调注入。
 */
@Composable
fun SettingsScreen(
    onNav: (NavItem) -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = AppGraph.factory)
    val context = LocalContext.current
    val meta by vm.meta.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }
    LaunchedEffect(vm) {
        vm.resetDone.collect { ok ->
            if (ok) {
                // pushEnabled 复位为 false → 撤销每日补给提醒任务（否则幽灵任务继续跑）
                DailySupplyNotifier.setEnabled(context, false)
                feedback.show("已重置存档")
            } else {
                feedback.show("重置失败，请重试")
            }
            showResetDialog = false
        }
    }

    // 推送（API 33+）运行时权限请求：拒绝不回滚开关，仅提示提醒将静默
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) scope.launch { feedback.show("未授予通知权限，补给提醒将不会显示") }
    }

    PageBackground {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "设 置", onBack = { onNav(NavItem.Home) })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("音频与体验")
                EntranceItem(index = 0) {
                    SettingSwitchRow(
                        title = "音效",
                        subtitle = "战斗与抽卡音效",
                        checked = meta.soundEnabled,
                        onCheckedChange = { enabled ->
                            vm.setSoundEnabled(enabled) { on ->
                                MilanAudio.setSfxVolume(if (on) 0.9f else 0f)
                            }
                        },
                    )
                }
                EntranceItem(index = 1) {
                    SettingSwitchRow(
                        title = "振动",
                        subtitle = "抽卡演出触觉反馈",
                        checked = meta.vibrationEnabled,
                        onCheckedChange = { enabled -> vm.setVibrationEnabled(enabled) },
                    )
                }
                EntranceItem(index = 2) {
                    SettingSwitchRow(
                        title = "推送",
                        subtitle = "每日补给刷新时本地提醒（12:00）",
                        checked = meta.pushEnabled,
                        onCheckedChange = { enabled ->
                            vm.setPushEnabled(enabled) { on ->
                                // 排程/撤销 WorkManager 周期任务（持久化，跨重启有效）
                                DailySupplyNotifier.setEnabled(context, on)
                                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !DailySupplyNotifier.canNotify(context)
                                ) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                    )
                }
                EntranceItem(index = 3) {
                    SettingSwitchRow(
                        title = "动效减弱",
                        subtitle = "关闭抽卡仪式等高负载演出（无障碍）",
                        checked = meta.reduceMotionEnabled,
                        onCheckedChange = { enabled -> vm.setReduceMotionEnabled(enabled) },
                    )
                }

                SectionTitle("数据管理")
                EntranceItem(index = 3) {
                    GlassPanel {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "重置存档",
                                    color = AppTheme.Text1,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "清除全部角色、货币与进度，无法恢复",
                                    color = AppTheme.Text2,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            NeonButton(
                                text = "重 置",
                                color = AppTheme.Danger,
                                onClick = { showResetDialog = true },
                            )
                        }
                    }
                }
                EntranceItem(index = 4) {
                    CrashLogCard(
                        onExport = {
                            // P0-C4：导出走挂起版脱离主线程 IO（大文件读 + 写镜像目录）
                            scope.launch {
                                val path = CrashReporter.exportAll()
                                feedback.show(path?.let { "已导出至 $it" } ?: "无崩溃日志可导出")
                            }
                        },
                    )
                }

                SectionTitle("AI 智能推荐")
                EntranceItem(index = 6) {
                    val recs by produceState(initialValue = emptyList<CharacterRecommendation>()) {
                        value = vm.recommendCharacters()
                    }
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("角色培养推荐", color = AppTheme.Gold, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            if (recs.isEmpty()) {
                                Text("暂无推荐", color = AppTheme.Text2, style = MaterialTheme.typography.labelLarge)
                            } else {
                                recs.take(3).forEach { rec ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                rec.characterName,
                                                color = AppTheme.Text1,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                rec.reasons.firstOrNull() ?: "",
                                                color = AppTheme.Text2,
                                                fontSize = 11.sp,
                                            )
                                        }
                                        Text(
                                            "评分 ${rec.score}",
                                            color = AppTheme.Gold,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                EntranceItem(index = 7) {
                    val gachaRec by produceState(initialValue = null as GachaRecommendation?) {
                        value = vm.recommendGacha()
                    }
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("抽卡策略", color = AppTheme.Gold, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            if (gachaRec != null) {
                                val g = gachaRec!!
                                Text(
                                    "累计 ${g.currentPity} 抽 · 可抽 ${g.pullsAvailable} 次",
                                    color = AppTheme.Text2,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                g.recommendations.take(2).forEach { tip ->
                                    Text(
                                        "· $tip",
                                        color = AppTheme.Text1,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "建议: ${g.suggestedPool}",
                                    color = AppTheme.Frost,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Text("暂无推荐", color = AppTheme.Text2, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                SectionTitle("关于")
                EntranceItem(index = 5) {
                    GlassPanel {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Milan · 星陨物语",
                                color = AppTheme.Text1,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "版本 $version",
                                color = AppTheme.Text2,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            GameNavBar(
                active = NavItem.Settings,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    ResetSaveDialog(
        show = showResetDialog,
        onDismiss = { showResetDialog = false },
        onConfirm = {
            // I13 对齐：重置是全档破坏性写操作，确认键补 busy 防双击（busy 在 VM 内）
            vm.resetSave()
        },
    )
}

/** 重置存档确认对话框（I12：从 SettingsScreen 主函数抽出；2026-08 起用 GlassDialog 替代 Material AlertDialog）。 */
@Composable
private fun ResetSaveDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        show = show,
        onDismiss = onDismiss,
        title = "重置存档",
        body = "将清除所有角色、货币与进度，且无法恢复。确定继续吗？",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonButton(text = "取 消", color = AppTheme.Text2, onClick = onDismiss)
            NeonButton(text = "确 定", color = AppTheme.Danger, onClick = onConfirm)
        }
    }
}

/** 设置开关行：标题 + 副文案 + GoldSwitch 熔金开关（2026-08 起替代 Material Switch）。 */
@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AppTheme.Text1, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = AppTheme.Text2, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
            }
            Spacer(Modifier.width(12.dp))
            GoldSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** 崩溃日志行：有未导出崩溃时提示数量，导出写入日志文件（无崩溃提示空）。 */
@Composable
private fun CrashLogCard(onExport: () -> Unit) {
    // P0-C4：崩溃计数脱离主线程读取（crashCount 已挂起），挂载时一次性拉取
    val crashCount by produceState(initialValue = 0) { value = CrashReporter.crashCount() }
    GlassPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("崩溃日志", color = AppTheme.Text1, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (crashCount > 0) "有 $crashCount 条日志待导出" else "当前无崩溃日志",
                    color = AppTheme.Text2,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(Modifier.width(12.dp))
            NeonButton(
                text = "导 出",
                color = AppTheme.FrostDeep,
                onClick = onExport,
            )
        }
    }
}
