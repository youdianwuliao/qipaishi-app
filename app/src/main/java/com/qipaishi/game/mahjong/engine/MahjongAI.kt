package com.qipaishi.game.mahjong.engine

import com.qipaishi.game.mahjong.model.*

/**
 * 麻将 AI — 最简策略
 */
object MahjongAI {

    /** 弃牌决策：优先打刚摸的牌，否则打孤张 */
    fun decideDiscard(hand: List<Tile>, drawnTile: Tile?): Tile {
        // 先打刚摸的牌
        if (drawnTile != null && hand.contains(drawnTile)) return drawnTile

        // 否则打出现次数最少、点数最大的孤张
        val freq = hand.groupBy { it.value }.mapValues { it.value.size }
        val singles = hand.filter { freq[it.value] == 1 }
        return if (singles.isNotEmpty()) singles.maxByOrNull { it.value } ?: hand.last()
        else hand.last()
    }

    /** 回应决策：返回应该执行的操作 */
    fun decideResponse(responses: PlayerResponses): String {
        return when {
            responses.canHu -> "hu"
            responses.canGang -> "gang"
            responses.canPeng -> "peng"
            responses.canChi -> "chi"
            else -> "pass"
        }
    }

    /** 吃牌选择：从选项中选最小的组合 */
    fun decideChi(options: List<List<Tile>>): List<Tile> {
        return options.minByOrNull { it.maxOf { t -> t.value } } ?: emptyList()
    }
}