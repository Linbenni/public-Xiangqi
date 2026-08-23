package com.sojourners.tchess

import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.sojourners.tchess.service.EngineService
import com.sojourners.tchess.settings.PerfProfile
import com.sojourners.tchess.system.ThermalMonitor
import com.sojourners.tchess.ui.analysis.AnalysisScreen
import com.sojourners.tchess.ui.analysis.AnalysisViewModel
import com.sojourners.tchess.ui.game.GameScreen
import com.sojourners.tchess.ui.game.GameViewModel
import com.sojourners.tchess.ui.manual.ManualScreen
import com.sojourners.tchess.ui.manual.ManualViewModel
import com.sojourners.tchess.ui.settings.SettingsScreen
import com.sojourners.tchess.ui.settings.SettingsViewModel
import com.sojourners.tchess.ui.theme.TchessTheme

/** 底部导航页签 */
enum class Tab(val label: String) {
    GAME("对弈"),
    MANUAL("棋谱"),
    ANALYSIS("分析"),
    SETTINGS("设置"),
}

class MainActivity : ComponentActivity() {

    private val gameVm: GameViewModel by viewModels()
    private val manualVm: ManualViewModel by viewModels()
    private val analysisVm: AnalysisViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通知权限（前台 Service 通知在 33+ 需要运行时授权，拒绝也不影响对局）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        }

        // 发热状态监听（API 29+，M3 性能档位提示）
        ThermalMonitor.init(this)

        setContent {
            TchessTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainTabs(
                        gameVm = gameVm,
                        manualVm = manualVm,
                        analysisVm = analysisVm,
                        settingsVm = settingsVm,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 回到前台：进程优先级已高，停掉前台 Service 与通知
        EngineService.stop(this)
    }

    override fun onStop() {
        super.onStop()
        // 切后台且对局未结束：前台 Service 保活引擎进程；结束后恢复局面由存档兜底
        if (gameVm.hasActiveGame()) {
            EngineService.start(this)
        } else {
            EngineService.stop(this)
        }
    }
}

@Composable
private fun MainTabs(
    gameVm: GameViewModel,
    manualVm: ManualViewModel,
    analysisVm: AnalysisViewModel,
    settingsVm: SettingsViewModel,
) {
    var tab by remember { mutableStateOf(Tab.GAME) }

    // 首次进入（含旋转重建）：绑定对弈页的引擎回调，否则引擎回包无人接收
    androidx.compose.runtime.LaunchedEffect(Unit) {
        gameVm.onScreenShown()
    }

    fun selectTab(newTab: Tab) {
        if (newTab == tab) return
        // 先隐藏旧页再进入新页：引擎绑定按页切换（对弈/分析共用同一进程）
        when (tab) {
            Tab.GAME -> gameVm.onScreenHidden()
            Tab.MANUAL -> manualVm.pause() // 离开棋谱页停掉复盘播放
            Tab.ANALYSIS -> analysisVm.onHidden()
            Tab.SETTINGS -> {}
        }
        when (newTab) {
            Tab.GAME -> gameVm.onScreenShown()
            Tab.MANUAL -> {}
            Tab.ANALYSIS -> analysisVm.onShown()
            Tab.SETTINGS -> {}
        }
        tab = newTab
    }

    Scaffold(
        topBar = { ThermalBanner(settingsVm) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.GAME,
                    onClick = { selectTab(Tab.GAME) },
                    icon = { Icon(Icons.Filled.Style, contentDescription = null) },
                    label = { Text(Tab.GAME.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.MANUAL,
                    onClick = { selectTab(Tab.MANUAL) },
                    icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                    label = { Text(Tab.MANUAL.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.ANALYSIS,
                    onClick = { selectTab(Tab.ANALYSIS) },
                    icon = { Icon(Icons.Filled.Analytics, contentDescription = null) },
                    label = { Text(Tab.ANALYSIS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { selectTab(Tab.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(Tab.SETTINGS.label) },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (tab) {
                Tab.GAME -> GameScreen(gameVm, onOpenAnalysis = {
                    com.sojourners.tchess.ui.analysis.AnalysisHandoff.offer(gameVm.exportForAnalysis())
                    selectTab(Tab.ANALYSIS)
                })
                Tab.MANUAL -> ManualScreen(manualVm)
                Tab.ANALYSIS -> AnalysisScreen(analysisVm)
                Tab.SETTINGS -> SettingsScreen(settingsVm)
            }
        }
    }
}

/**
 * M3 发热降频提示：SEVERE 及以上一律提示；MODERATE 仅全力档提示。
 */
@Composable
private fun ThermalBanner(settingsVm: SettingsViewModel) {
    val status by ThermalMonitor.status.collectAsState()
    val s by settingsVm.settings.collectAsState()
    if (!ThermalMonitor.shouldWarn(status, fullPowerProfile = s.perfProfile == PerfProfile.FULL)) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "设备温度较高、可能已降频 —— 建议切到「省电」档或稍作休息",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
