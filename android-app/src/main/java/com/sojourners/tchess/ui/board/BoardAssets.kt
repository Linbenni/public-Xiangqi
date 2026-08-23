package com.sojourners.tchess.ui.board

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * 棋盘素材（复用桌面 ui 目录下的棋盘与 14 枚棋子 PNG，映射同 CustomBoardRender）。
 */
class BoardAssets(
    val boardImage: ImageBitmap,
    val pieces: Map<Char, ImageBitmap>,
)

private val PIECE_FILES = mapOf(
    'r' to "br", 'n' to "bn", 'b' to "bb", 'a' to "ba", 'k' to "bk", 'c' to "bc", 'p' to "bp",
    'R' to "rr", 'N' to "rn", 'B' to "rb", 'A' to "ra", 'K' to "rk", 'C' to "rc", 'P' to "rp",
)

@Composable
fun rememberBoardAssets(): BoardAssets? {
    val context = LocalContext.current
    return remember {
        try {
            val board = BitmapFactory.decodeStream(context.assets.open("board/board.png")).asImageBitmap()
            val map = HashMap<Char, ImageBitmap>(PIECE_FILES.size)
            PIECE_FILES.forEach { (ch, file) ->
                map[ch] = BitmapFactory.decodeStream(context.assets.open("board/$file.png")).asImageBitmap()
            }
            BoardAssets(board, map)
        } catch (_: Exception) {
            null
        }
    }
}
