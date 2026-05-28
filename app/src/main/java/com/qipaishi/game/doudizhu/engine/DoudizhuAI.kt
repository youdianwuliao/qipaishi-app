package com.qipaishi.game.doudizhu.engine

import com.qipaishi.game.doudizhu.model.*

/**
 * 斗地主 AI — 最简可行版
 *
 * 策略：
 * - 叫地主：数大牌（2/王/ACE），大牌多就抢
 * - 出牌（自由出）：从最小的单张开始
 * - 出牌（回应）：找最小能打过的牌型，找不到就 pass
 */
object DoudizhuAI {

    /** 叫地主决策：返回叫分 0-3 */
    fun decideBid(hand: List<Card>, currentHighestBid: Int): Int {
        if (hand.size < 17) return 0  // 非正常手牌
        val strongCount = hand.count { it.rank >= 14 }  // A, 2, 王
        val bombCount = hand.groupBy { it.rank }.count { it.value.size == 4 }

        val score = when {
            strongCount >= 5 || bombCount >= 2 -> 3
            strongCount >= 4 || bombCount >= 1 -> 2
            strongCount >= 2 -> 1
            else -> 0
        }
        return if (score > currentHighestBid) score else 0
    }

    /** 自由出牌（新一轮，没有上一轮出牌要回应） */
    fun decideFreePlay(hand: List<Card>): List<Card> {
        if (hand.size == 1) return listOf(hand[0])

        // 优先出单张最小的
        return listOf(hand.first())
    }

    /** 回应出牌：找到最小能打过的牌型，找不到返回空列表表示 pass */
    fun decideResponse(hand: List<Card>, lastPlay: PlayedCards?): List<Card> {
        if (lastPlay == null) return decideFreePlay(hand)
        val lastGroup = lastPlay.group

        // 1. 先检查火箭
        val hasRocket = hand.count { it.isJoker } == 2
        if (hasRocket && lastGroup.type != CardGroupType.ROCKET) {
            return hand.filter { it.isJoker }
        }

        // 2. 检查炸弹
        val bombRanks = hand.groupBy { it.rank }
            .filter { it.value.size >= 4 }
            .keys.sorted()
        for (rank in bombRanks) {
            val bombCards = hand.filter { it.rank == rank }.take(4)
            val bombGroup = CardGroup(CardGroupType.BOMB, bombCards, rank)
            if (CardComparator.canBeat(bombGroup, lastGroup)) {
                return bombCards
            }
        }

        // 3. 根据上一轮牌型找同类型
        val type = lastGroup.type
        val needSize = lastGroup.cards.size

        when (type) {
            CardGroupType.SINGLE -> {
                // 找比 lastGroup.keyRank 大的最小单张
                val candidates = hand.filter { !it.isJoker && it.rank > lastGroup.keyRank }
                    .sortedBy { it.rank }
                if (candidates.isNotEmpty()) return listOf(candidates.first())
                // 如果普通牌没有，用炸弹（上面已经处理）
            }
            CardGroupType.PAIR -> {
                val rankCounts = hand.groupBy { it.rank }.mapValues { it.value.size }
                val candidates = rankCounts.filter { it.value >= 2 && it.key > lastGroup.keyRank }
                    .keys.sorted()
                if (candidates.isNotEmpty()) {
                    return hand.filter { it.rank == candidates.first() }.take(2)
                }
            }
            CardGroupType.TRIPLE -> {
                val rankCounts = hand.groupBy { it.rank }.mapValues { it.value.size }
                val candidates = rankCounts.filter { it.value >= 3 && it.key > lastGroup.keyRank }
                    .keys.sorted()
                if (candidates.isNotEmpty()) {
                    return hand.filter { it.rank == candidates.first() }.take(3)
                }
            }
            CardGroupType.STRAIGHT -> {
                return findStraightResponse(hand, lastGroup.keyRank, needSize)
            }
            CardGroupType.DOUBLE_STRAIGHT -> {
                return findDoubleStraightResponse(hand, lastGroup.keyRank, needSize / 2)
            }
            CardGroupType.TRIPLE_ONE, CardGroupType.TRIPLE_TWO -> {
                val rankCounts = hand.groupBy { it.rank }.mapValues { it.value.size }
                val candidates = rankCounts.filter { it.value >= 3 && it.key > lastGroup.keyRank }
                    .keys.sorted()
                if (candidates.isNotEmpty()) {
                    val tripleCards = hand.filter { it.rank == candidates.first() }.take(3)
                    val remaining = hand.filter { it !in tripleCards }.sortedBy { it.rank }
                    return if (type == CardGroupType.TRIPLE_ONE) {
                        tripleCards + remaining.take(1)
                    } else {
                        val pairRanks = remaining.groupBy { it.rank }.filter { it.value.size >= 2 }.keys.sorted()
                        if (pairRanks.isNotEmpty()) tripleCards + remaining.filter { it.rank == pairRanks.first() }.take(2)
                        else tripleCards + remaining.take(1)  // 降级为三带一
                    }
                }
            }
            CardGroupType.BOMB -> {
                // 炸弹只能更大的炸弹或火箭打（已在上面的炸弹循环中处理）
                // 火箭已在上面处理
            }
            else -> {}
        }

        // 找不到同类型 → pass
        return emptyList()
    }

    private fun findStraightResponse(hand: List<Card>, minKeyRank: Int, length: Int): List<Card> {
        val rankCounts = hand.filter { it.rank < 15 }.groupBy { it.rank }.mapValues { it.value.size }
        val available = rankCounts.filter { it.value >= 1 }.keys.sorted()

        for (start in available) {
            if (start <= minKeyRank) continue
            val needed = (start until start + length).toList()
            if (needed.all { it in available } && needed.last() < 15) {
                val result = mutableListOf<Card>()
                for (rank in needed) {
                    result.add(hand.first { it.rank == rank })
                }
                return result.sorted()
            }
        }
        return emptyList()
    }

    private fun findDoubleStraightResponse(hand: List<Card>, minKeyRank: Int, pairCount: Int): List<Card> {
        val rankCounts = hand.filter { it.rank < 15 }.groupBy { it.rank }.mapValues { it.value.size }
        val available = rankCounts.filter { it.value >= 2 }.keys.sorted()

        for (start in available) {
            if (start <= minKeyRank) continue
            val needed = (start until start + pairCount).toList()
            if (needed.all { it in available } && needed.last() < 15) {
                val result = mutableListOf<Card>()
                for (rank in needed) {
                    result.addAll(hand.filter { it.rank == rank }.take(2))
                }
                return result.sorted()
            }
        }
        return emptyList()
    }
}