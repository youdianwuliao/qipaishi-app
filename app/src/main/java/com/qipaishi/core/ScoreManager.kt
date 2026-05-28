package com.qipaishi.core

/**
 * 积分管理器
 *
 * 本地存储玩家积分，管理押分、结算、破产。
 */
class ScoreManager(private val playerId: String) {

    data class PlayerScore(
        val playerId: String,
        val playerName: String,
        var points: Int
    )

    private val scores = mutableMapOf<String, PlayerScore>()

    /**
     * 初始化/获取玩家积分
     */
    fun getOrCreate(id: String, name: String): PlayerScore {
        return scores.getOrPut(id) { PlayerScore(id, name, INITIAL_POINTS) }
    }

    fun getPoints(id: String): Int = scores[id]?.points ?: 0
    fun setPoints(id: String, points: Int) { scores[id]?.points = points }
    fun addPoints(id: String, delta: Int) {
        val s = scores[id] ?: return
        s.points = (s.points + delta).coerceAtLeast(0)
    }

    /**
     * 检查是否足够入场
     */
    fun canEnter(id: String): Boolean {
        val p = scores[id]?.points ?: INITIAL_POINTS
        return p >= MIN_ENTRY_POINTS  // 至少 3000（底注 1000 × 最大叫分 3）
    }

    /**
     * 结算一局（斗地主）
     * scores 是 Map<playerIndex, delta>
     * playerIds 是 playerIndex → playerId 映射
     */
    fun settleGame(playerIds: Map<Int, String>, scoreDeltas: Map<Int, Int>) {
        scoreDeltas.forEach { (index, delta) ->
            val id = playerIds[index] ?: return@forEach
            addPoints(id, delta)
        }
    }

    /**
     * 破产处理：积分归零时重置
     */
    fun handleBankrupt(id: String): Int {
        val s = scores[id] ?: return 0
        if (s.points <= 0) {
            s.points = RESET_POINTS
        }
        return s.points
    }

    /**
     * 导出所有玩家积分（用于排名显示）
     */
    fun getAllScores(): List<PlayerScore> = scores.values.sortedByDescending { it.points }

    companion object {
        const val INITIAL_POINTS = 10000
        const val MIN_ENTRY_POINTS = 3000   // 底注 × 最大倍数
        const val RESET_POINTS = 5000        // 破产后重置
    }
}