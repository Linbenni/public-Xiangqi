package com.sojourners.tchess.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sojourners.tchess.settings.BookSettings
import com.sojourners.tchess.settings.EngineSettings
import com.sojourners.tchess.settings.PerfProfile
import com.sojourners.tchess.settings.TimeControl

private val HASH_CHOICES = listOf(16, 32, 64, 128, 256)

/**
 * M3 设置中心：性能档位 / 引擎参数 / 时间控制 / 关于。
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionTitle("手机性能档位")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerfProfile.entries.forEach { p ->
                FilterChip(
                    selected = s.perfProfile == p,
                    onClick = { vm.applyProfile(p) },
                    label = { Text(p.label) },
                )
            }
        }
        Text(
            text = "${s.perfProfile.desc}；档位是线程/内存预设，可在下方微调",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SectionTitle("引擎")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用引擎", modifier = Modifier.weight(1f))
            Switch(checked = s.engineEnabled, onCheckedChange = { vm.setEngineEnabled(it) })
        }
        Text(
            text = if (!s.engineEnabled) "关闭后人机对弈与分析不可用，仅双人模式"
            else if (vm.engineAvailable) "内置 Pikafish（UCI · GPLv3）"
            else "未找到 libpikafish.so，人机功能不可用",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))
        DiscreteSliderRow(
            label = "线程数",
            valueText = "${s.threads}",
            value = s.threads.toFloat(),
            range = EngineSettings.MIN_THREADS.toFloat()..EngineSettings.MAX_THREADS.toFloat(),
            steps = EngineSettings.MAX_THREADS - EngineSettings.MIN_THREADS - 1,
            enabled = s.engineEnabled,
        ) { vm.setThreads(it.toInt()) }

        val hashIndex = HASH_CHOICES.indexOf(s.hashMB).coerceAtLeast(0)
        DiscreteSliderRow(
            label = "置换表 Hash",
            valueText = "${s.hashMB} MB",
            value = hashIndex.toFloat(),
            range = 0f..(HASH_CHOICES.size - 1).toFloat(),
            steps = 0,
            enabled = s.engineEnabled,
        ) { vm.setHashMB(HASH_CHOICES[it.toInt().coerceIn(0, HASH_CHOICES.size - 1)]) }

        Spacer(Modifier.height(8.dp))
        Text("MultiPV 搜索广度：${s.multiPV}", style = MaterialTheme.typography.bodyMedium)
        CommitOnReleaseSlider(
            value = s.multiPV.toFloat(),
            range = 1f..EngineSettings.MAX_MULTI_PV.toFloat(),
            steps = EngineSettings.MAX_MULTI_PV - 2,
            enabled = s.engineEnabled,
        ) { vm.setMultiPV(it.toInt()) }
        Text(
            text = "分析列表同时给出的候选变化条数；1 = 仅主变",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SectionTitle("时间控制")
        TimeControlSection(vm, s)

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SectionTitle("开局库")
        BookSection(vm)

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SectionTitle("关于")
        Text(
            text = "TCHESS 安卓版 · 内置引擎 Pikafish（GPLv3）\n" +
                "引擎源码：github.com/official-pikafish/Pikafish\n" +
                "依 GPLv3 随应用分发，源码获取见仓库说明。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TimeControlSection(vm: SettingsViewModel, s: EngineSettings) {
    var valueText by remember(s.timeValue, s.timeControl) { mutableStateOf(s.timeValue.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column {
        TimeControl.entries.forEach { tc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.timeControl == tc, onClick = {
                    errorText = null
                    vm.setTimeControl(tc, valueText.toLongOrNull() ?: s.timeValue)
                })
                Text(tc.label)
                if (tc != TimeControl.INFINITE) {
                    Spacer(Modifier.width(8.dp))
                    Text("· ${tc.unitHint}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (s.timeControl != TimeControl.INFINITE) {
            OutlinedTextField(
                value = valueText,
                onValueChange = { input -> valueText = input.filter { c -> c.isDigit() } },
                label = { Text("数值（${s.timeControl.unitHint}）") },
                isError = errorText != null,
                supportingText = errorText?.let { e -> { Text(e) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    val v = valueText.toLongOrNull()
                    if (v == null || v <= 0) {
                        // 与桌面 TimeSettingController 校验文案一致
                        errorText = when (s.timeControl) {
                            TimeControl.FIXED_STEPS -> "层数错误"
                            TimeControl.FIXED_NODES -> "节点数错误"
                            else -> "时间错误"
                        }
                    } else {
                        errorText = null
                        vm.setTimeControl(s.timeControl, v)
                    }
                }) { Text("应用") }
            }
        }
        Text(
            text = "作用于分析模式；对弈难度使用独立的固定时间制（快棋/均衡/深思）",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

// ---------------------------------------------------------------- M4 开局库

/** 选着规则中文文案（与桌面 BookSettingController 一致） */
private val MOVE_RULE_LABELS = mapOf(
    com.sojourners.chess.openbook.MoveRule.BEST_SCORE to "取最高分",
    com.sojourners.chess.openbook.MoveRule.BEST_WINRATE to "取最高胜率",
    com.sojourners.chess.openbook.MoveRule.POSITIVE_RANDOM to "正分数随机",
    com.sojourners.chess.openbook.MoveRule.FULL_RANDOM to "完全随机",
)

private val CLOUD_TIMEOUT_CHOICES = listOf(1000, 2000, 5000, 8000, 15000)

@Composable
private fun BookSection(vm: SettingsViewModel) {
    val b by vm.bookSettings.collectAsState()
    val books by vm.openBooks.collectAsState()
    val message by vm.bookMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importBook(it, queryDisplayName(context, it)) } }

    Column {
        // 总开关：对弈/分析自动挂库
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用开局库", modifier = Modifier.weight(1f))
            Switch(checked = b.bookSwitch, onCheckedChange = { vm.setBookSwitch(it) })
        }
        Text(
            text = "开启后对弈自动走库着、分析页展示库着法（core OpenBookManager）",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("云端开局库", modifier = Modifier.weight(1f))
            Switch(
                checked = b.useCloudBook,
                onCheckedChange = { vm.setUseCloudBook(it) },
                enabled = b.bookSwitch,
            )
        }
        if (b.useCloudBook) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("云库只取终局着法", modifier = Modifier.weight(1f))
                Switch(checked = b.onlyCloudFinalPhase, onCheckedChange = { vm.setOnlyCloudFinalPhase(it) })
            }
            val timeoutIndex = CLOUD_TIMEOUT_CHOICES.indexOf(b.cloudBookTimeout).coerceAtLeast(0)
            DiscreteSliderRow(
                label = "云库超时",
                valueText = "${b.cloudBookTimeout} ms",
                value = timeoutIndex.toFloat(),
                range = 0f..(CLOUD_TIMEOUT_CHOICES.size - 1).toFloat(),
                steps = 0,
                enabled = true,
            ) { vm.setCloudTimeout(CLOUD_TIMEOUT_CHOICES[it.toInt().coerceIn(0, CLOUD_TIMEOUT_CHOICES.size - 1)]) }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本地库优先", modifier = Modifier.weight(1f))
            Switch(
                checked = b.localBookFirst,
                onCheckedChange = { vm.setLocalBookFirst(it) },
                enabled = b.bookSwitch && b.useCloudBook,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text("选着规则", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            MOVE_RULE_LABELS.forEach { (rule, label) ->
                FilterChip(
                    selected = b.moveRule == rule,
                    onClick = { vm.setMoveRule(rule) },
                    label = { Text(label) },
                    enabled = b.bookSwitch,
                )
            }
        }

        DiscreteSliderRow(
            label = "脱离开局库步数（回合）",
            valueText = "${b.offManualSteps}",
            value = b.offManualSteps.toFloat(),
            range = BookSettings.MIN_OFF_MANUAL_STEPS.toFloat()..BookSettings.MAX_OFF_MANUAL_STEPS.toFloat(),
            steps = BookSettings.MAX_OFF_MANUAL_STEPS - BookSettings.MIN_OFF_MANUAL_STEPS - 1,
            enabled = b.bookSwitch,
        ) { vm.setOffManualSteps(it.toInt()) }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = b.bookSwitch) {
                Text("导入本地库")
            }
            Text("支持 ${com.sojourners.tchess.book.BookNames.SUPPORTED_KEYS}", style = MaterialTheme.typography.bodySmall)
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).clickable { vm.dismissBookMessage() },
            )
        }

        if (books.isEmpty()) {
            Text(
                text = "尚未导入本地库；文件将复制到应用私有目录（books/）",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            books.forEachIndexed { index, path ->
                val name = path.substringAfterLast('/')
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.moveBook(index, -1) }, enabled = index > 0) { Text("↑") }
                    TextButton(onClick = { vm.moveBook(index, +1) }, enabled = index < books.size - 1) { Text("↓") }
                    TextButton(onClick = { vm.removeBook(index) }) { Text("删除") }
                }
            }
        }
    }
}

/** SAF 文件名解析（失败返回 null → VM 提示不支持） */
private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? = try {
    context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null, null, null,
    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
} catch (_: Exception) {
    null
}

/** 拖动结束才提交的离散滑杆行 */
@Composable
private fun DiscreteSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        CommitOnReleaseSlider(value, range, steps, enabled, onCommit)
    }
}

@Composable
private fun CommitOnReleaseSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember(value) { mutableStateOf(false) }
    var local by remember(value) { mutableStateOf(value) }
    Slider(
        value = (if (dragging) local else value).coerceIn(range.start, range.endInclusive),
        onValueChange = {
            local = it
            dragging = true
        },
        onValueChangeFinished = {
            dragging = false
            onCommit(local)
        },
        valueRange = range,
        steps = steps,
        enabled = enabled,
    )
}
