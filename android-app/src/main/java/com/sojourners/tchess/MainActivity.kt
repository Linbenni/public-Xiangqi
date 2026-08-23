package com.sojourners.tchess

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.sojourners.tchess.service.EngineService
import com.sojourners.tchess.ui.game.GameScreen
import com.sojourners.tchess.ui.game.GameViewModel
import com.sojourners.tchess.ui.theme.TchessTheme

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

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

        setContent {
            TchessTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameScreen(vm)
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
        if (vm.hasActiveGame()) {
            EngineService.start(this)
        } else {
            EngineService.stop(this)
        }
    }
}
