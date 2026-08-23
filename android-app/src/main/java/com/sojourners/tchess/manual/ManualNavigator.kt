package com.sojourners.tchess.manual

import com.sojourners.chess.manual.ChessManual
import com.sojourners.chess.model.ManualRecord

/**
 * 棋谱行导航模型（M4，纯 JVM 可单测）。
 *
 * 复刻桌面 `ChessManualHandle` 的语义：
 * - [line] 是从根节点沿各节点选中分支（[ManualRecord.getNext] 下标）物化出的当前线；
 * - [p] 为当前所在下标（0 = 开始局面节点）；
 * - 前进/后退/开局/终局/点击跳转 = 移动 [p]；
 * - 切换变着 = 改写当前节点的 next 并重建其后的线尾。
 */
class ManualNavigator {

    private var startFen: String = ""
    private var head: ManualRecord? = null
    private val line = ArrayList<ManualRecord>()
    private var p = 0

    val isOpen: Boolean get() = head != null
    val position: Int get() = p
    val size: Int get() = line.size
    val fenCode: String get() = startFen

    /** 当前线的节点快照（只读视图，UI 渲染用） */
    fun nodes(): List<ManualRecord> = line

    fun nodeAt(index: Int): ManualRecord? = line.getOrNull(index)

    fun currentNode(): ManualRecord? = line.getOrNull(p)

    /** 当前节点的全部变着 */
    fun childrenOfCurrent(): List<ManualRecord> = currentNode()?.list ?: emptyList()

    /** 装载一份已解析棋谱：按 next 链物化主线 */
    fun open(cm: ChessManual) {
        startFen = cm.fenCode ?: ""
        head = cm.head
        p = 0
        rebuildLineFrom(head)
    }

    fun close() {
        startFen = ""
        head = null
        line.clear()
        p = 0
    }

    private fun rebuildLineFrom(startNode: ManualRecord?) {
        line.clear()
        var n = startNode
        while (n != null) {
            line.add(n)
            n = if (n.list.isEmpty()) null else n.list[n.next.coerceIn(0, n.list.size - 1)]
        }
    }

    // ---------------------------------------------------------------- 行棋方（与桌面 getRedGo 同语义）

    /** 起始 FEN 是否红先行（等价桌面 contains("w")：合法 FEN 其余字符不含 w） */
    fun startRedToGo(): Boolean {
        val second = startFen.split(" ").getOrNull(1) ?: return true
        return !second.equals("b", ignoreCase = true)
    }

    /** 当前下标处的行棋方 */
    fun redGoAt(index: Int): Boolean {
        var red = startRedToGo()
        if (index % 2 != 0) red = !red
        return red
    }

    fun currentRedToGo(): Boolean = redGoAt(p)

    // ---------------------------------------------------------------- 导航（返回是否有变化）

    fun toStart(): Boolean {
        if (p == 0 || line.isEmpty()) return false
        p = 0
        return true
    }

    fun back(): Boolean {
        if (p <= 0) return false
        p--
        return true
    }

    fun forward(): Boolean {
        if (p >= line.size - 1) return false
        p++
        return true
    }

    fun toEnd(): Boolean {
        if (p >= line.size - 1 || line.isEmpty()) return false
        p = line.size - 1
        return true
    }

    /** 点击着法列表跳转 */
    fun jumpTo(index: Int): Boolean {
        if (index < 0 || index >= line.size || index == p) return false
        p = index
        return true
    }

    /**
     * 在当前节点切换变着（子着法下标）：改写 next 并重建线尾，前进到新分支首着。
     * 与桌面 boardMove 双击子着法一致。
     */
    fun switchBranch(childIndex: Int): Boolean {
        val node = currentNode() ?: return false
        if (childIndex < 0 || childIndex >= node.list.size) return false
        node.next = childIndex
        var n: ManualRecord? = node.list[childIndex]
        while (line.size - 1 > p) line.removeAt(line.size - 1)
        while (n != null) {
            line.add(n)
            n = if (n.list.isEmpty()) null else n.list[n.next.coerceIn(0, n.list.size - 1)]
        }
        p++
        return true
    }

    /** 上一个有变着的节点（不含当前） */
    fun prevBranchJump(): Boolean {
        for (i in p - 1 downTo 0) {
            if (line[i].list.size > 1) {
                p = i
                return true
            }
        }
        return false
    }

    /** 下一个有变着的节点（不含当前） */
    fun nextBranchJump(): Boolean {
        for (i in p + 1 until line.size) {
            if (line[i].list.size > 1) {
                p = i
                return true
            }
        }
        return false
    }

    /** 当前位置之前的引擎坐标着法序列（喂给 GameLogic 重放 / 引擎 position moves） */
    fun moveListUpTo(index: Int): List<String> {
        if (index <= 0) return emptyList()
        val out = ArrayList<String>(index)
        for (i in 1..index.coerceAtMost(line.size - 1)) {
            out.add(line[i].move ?: "")
        }
        return out
    }

    fun moveList(): List<String> = moveListUpTo(p)
}
