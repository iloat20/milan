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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.DailySupplyNotifier
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 设置页（C# SettingsPage 翻译）。
 *
 * 结构：音频与体验（音效/振动/推送三开关）→ 数据管理（重置存档、崩溃日志导出）→ 关于。
 * 开关走 GameService 事务方法（落盘失败回滚，本页同步回滚本地状态并提示）；
 * 音效开关即时应用 MilanAudio 音量；振动开关由 GachaScreen 演出读取生效。
 */
@Composable
fun SettingsScreen(
    onNav: (NavItem) -> Unit,
) {
    val service = GameState.service
    val context = LocalContext.current
    // I5：开关状态从 GameSnapshot 派生（单一事实来源）。toggle 成功即由 persistSetting→refreshSnapshot
    // 推进快照，重置存档后快照自动复位，无需本地镜像与手工回滚。
    val snap = service.snapshot.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    // I13：in-flight 防重入——设置项落盘期间禁用二次触发，避免快速双击造成重复写。
    var busy by remember { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    // 2026-08 主线程 IO 异步化：设置/重置为 suspend（落盘在 IO 线程），用页面协程调用
    val scope = rememberCoroutineScope()

    // 推送（API 33+）运行时权限请求：拒绝不回滚开关，仅提示提醒将静默
    // （launcher 回调非协程上下文，feedback.show 为 suspend 需经 scope.launch）
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
                SettingSwitchRow(
                    title = "音效",
                    subtitle = "战斗与抽卡音效",
                    checked = snap.value.soundEnabled,
                    onCheckedChange = { enabled ->
                        if (busy) return@SettingSwitchRow
                        busy = true
                        scope.launch {
                            try {
                                when (service.setSoundEnabled(enabled)) {
                                    WriteOutcome.Success -> MilanAudio.setSfxVolume(if (enabled) 0.9f else 0f)
                                    WriteOutcome.Rejected -> feedback.show("设置失败")
                                    WriteOutcome.SaveFailed -> feedback.show("保存失败，请重试")
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
                SettingSwitchRow(
                    title = "振动",
                    subtitle = "抽卡演出触觉反馈",
                    checked = snap.value.vibrationEnabled,
                    onCheckedChange = { enabled ->
                        if (busy) return@SettingSwitchRow
                        busy = true
                        scope.launch {
                            try {
                                when (service.setVibrationEnabled(enabled)) {
                                    WriteOutcome.Success -> { /* 快照已推进，UI 从 snapshot 派生 */ }
                                    WriteOutcome.Rejected -> feedback.show("设置失败")
                                    WriteOutcome.SaveFailed -> feedback.show("保存失败，请重试")
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
                SettingSwitchRow(
                    title = "推送",
                    subtitle = "每日补给刷新时本地提醒（12:00）",
                    checked = snap.value.pushEnabled,
                    onCheckedChange = { enabled ->
                        if (busy) return@SettingSwitchRow
                        busy = true
                        scope.launch {
                            try {
                                when (service.setPushEnabled(enabled)) {
                                    WriteOutcome.Success -> {
                                        // 排程/撤销 WorkManager 周期任务（持久化，跨重启有效）
                                        DailySupplyNotifier.setEnabled(context, enabled)
                                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            !DailySupplyNotifier.canNotify(context)
                                        ) {
                                            // API 33+ 运行时权限：未授权时发起请求（拒绝则提醒静默，不回滚开关）
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    WriteOutcome.Rejected -> feedback.show("设置失败")
                                    WriteOutcome.SaveFailed -> feedback.show("保存失败，请重试")
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                )

                SectionTitle("数据管理")
                GlassPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("重置存档", color = AppTheme.Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("清除全部角色、货币与进度，无法恢复", color = AppTheme.Text2, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        NeonButton(
                            text = "重 置",
                            color = AppTheme.Danger,
                            onClick = { showResetDialog = true },
                        )
                    }
                }
                CrashLogCard(                onExport = {
                    // P0-C4：导出走挂起版脱离主线程 IO（大文件读 + 写镜像目录）
                    scope.launch {
                        val path = CrashReporter.exportAll()
                        feedback.show(path?.let { "已导出至 $it" } ?: "无崩溃日志可导出")
                    }
                })

                SectionTitle("关于")
                GlassPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Milan · 星陨物语", color = AppTheme.Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("版本 $version", color = AppTheme.Text2, fontSize = 13.sp)
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
            // I13 对齐：重置是全档破坏性写操作，确认键补 busy 防双击（其余写操作均有，此前此处漏了）
            if (busy) return@ResetSaveDialog
            busy = true
            scope.launch {
                try {
                    val ok = service.resetSave()
                    if (ok) {
                        // 开关状态随新档复位：resetSave 已 refreshSnapshot，UI 从 snapshot 派生自动复位；
                        // pushEnabled 复位为 false → 撤销每日补给提醒任务（否则幽灵任务继续跑）
                        DailySupplyNotifier.setEnabled(context, false)
                        feedback.show("已重置存档")
                    } else {
                        feedback.show("重置失败，请重试")
                    }
                } finally {
                    busy = false
                    showResetDialog = false
                }
            }
        },
    )
}

/** 重置存档确认对话框（I12：从 SettingsScreen 主函数抽出，收窄主函数职责）。 */
@Composable
private fun ResetSaveDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重置存档", fontWeight = FontWeight.Bold) },
        text = { Text("将清除所有角色、货币与进度，且无法恢复。确定继续吗？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确 定", color = AppTheme.Danger, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取 消", color = AppTheme.Text2)
            }
        },
    )
}

/** 设置开关行：标题 + 副文案 + Material 开关（开启态金色，与主题一致）。 */
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
                Text(title, color = AppTheme.Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = AppTheme.Text2, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AppTheme.Gold,
                    checkedThumbColor = AppTheme.GoldTextOn,
                    uncheckedTrackColor = AppTheme.Surface,
                    uncheckedThumbColor = AppTheme.Text3,
                ),
            )
        }
    }
}

/** 崩溃日志行：有未导出崩溃时提示数量，导出写入日志文件（无崩溃提示空）。 */
@Composable
private fun CrashLogCard(onExport: () -> Unit) {
    // P0-C4：崩溃计数脱离主线程读取（crashCount 已挂起），挂载时一次性拉取
    var crashCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { crashCount = CrashReporter.crashCount() }
    GlassPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("崩溃日志", color = AppTheme.Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (crashCount > 0) "有 $crashCount 条日志待导出" else "当前无崩溃日志",
                    color = AppTheme.Text2,
                    fontSize = 12.sp,
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