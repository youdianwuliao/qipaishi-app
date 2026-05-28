package com.qipaishi.game.doudizhu.model

/**
 * 牌型解析器 — 判断一组牌属于哪种牌型
 *
 * 使用频率统计法：
 * 1. 按点数分组，得到每个点数的张数
 * 2. 根据张数模式匹配牌型
 */
object CardGroupParser {

    /**
     * 解析牌型，无法识别返回 null
     */
    fun parse(rawCards: List<Card>): CardGroup? {
        if (rawCards.isEmpty()) return null

        val sorted = rawCards.sorted()
        val count = rawCards.size

        // 频率统计：rank → count
        val freq = sorted.groupBy { it.rank }.mapValues { it.value.size }
        // 按点数分组的结果（如 {3→4, 5→1, 14→2}）
        val groupByCount = freq.entries.groupBy({ it.value }, { it.key })
            .mapValues { it.value.sorted() }

        when (count) {
            1 -> return parseSingle(sorted)
            2 -> return parsePair(sorted) ?: parseRocket(sorted)
            3 -> return parseTriple(sorted)
            4 -> return parseBomb(sorted) ?: parseTripleOne(sorted)
            5 -> return parseStraight(sorted, groupByCount) ?: parseTripleTwo(sorted)
            6 -> return parseBomb(sorted)
                ?: parseStraight(sorted, groupByCount)
                ?: parseDoubleStraight(sorted, groupByCount, freq)
                ?: parseAirplaneNoWing(sorted, groupByCount)
                ?: parseTripleOne(sorted)
                ?: parseTripleTwo(sorted)
                ?: parseFourTwo(sorted, groupByCount)
            8 -> return parseStraight(sorted, groupByCount)
                ?: parseDoubleStraight(sorted, groupByCount, freq)
                ?: parseAirplaneSingle(sorted, freq)
                ?: parseAirplaneNoWing(sorted, groupByCount)
                ?: parseFourTwo(sorted, groupByCount)
            10 -> return parseStraight(sorted, groupByCount)
                ?: parseDoubleStraight(sorted, groupByCount, freq)
                ?: parseTripleTwo(sorted)
                ?: parseAirplanePair(sorted, freq)
                ?: parseFourTwo(sorted, groupByCount)
            else -> {
                // 多张牌：先试顺子类
                return parseStraight(sorted, groupByCount)
                    ?: parseDoubleStraight(sorted, groupByCount, freq)
                    ?: parseAirplaneSingle(sorted, freq)
                    ?: parseAirplanePair(sorted, freq)
                    ?: parseAirplaneNoWing(sorted, groupByCount)
                    ?: parseFourTwo(sorted, groupByCount)
            }
        }
    }

    // ================================================================
    // 各牌型解析
    // ================================================================

    private fun parseSingle(cards: List<Card>): CardGroup {
        return CardGroup(CardGroupType.SINGLE, cards, cards[0].rank)
    }

    private fun parsePair(cards: List<Card>): CardGroup? {
        if (cards.size != 2) return null
        if (cards[0].rank != cards[1].rank) return null
        return CardGroup(CardGroupType.PAIR, cards, cards[0].rank)
    }

    private fun parseTriple(cards: List<Card>): CardGroup? {
        if (cards.size != 3) return null
        if (cards[0].rank != cards[1].rank || cards[1].rank != cards[2].rank) return null
        return CardGroup(CardGroupType.TRIPLE, cards, cards[0].rank)
    }

    private fun parseTripleOne(cards: List<Card>): CardGroup? {
        if (cards.size != 4) return null
        val freq = cards.groupBy { it.rank }.mapValues { it.value.size }
        val tripleRank = freq.entries.find { it.value == 3 }?.key ?: return null
        val singleRank = freq.entries.find { it.value == 1 }?.key ?: return null
        return CardGroup(CardGroupType.TRIPLE_ONE, cards, tripleRank)
    }

    private fun parseTripleTwo(cards: List<Card>): CardGroup? {
        if (cards.size != 5) return null
        val freq = cards.groupBy { it.rank }.mapValues { it.value.size }
        val tripleRank = freq.entries.find { it.value == 3 }?.key ?: return null
        val pairRank = freq.entries.find { it.value == 2 }?.key ?: return null
        return CardGroup(CardGroupType.TRIPLE_TWO, cards, tripleRank)
    }

    /**
     * 顺子：≥5 张连续单牌，不能含 2 和王
     */
    private fun parseStraight(cards: List<Card>, byCount: Map<Int, List<Int>>): CardGroup? {
        if (cards.size < 5) return null
        // 所有牌必须是单张
        val singles = byCount[1] ?: return null
        if (singles.size != cards.size) return null
        // 最大不能 ≥15（不含 2 和王）
        if (singles.any { it >= 15 }) return null
        // 必须连续
        return if (isConsecutive(singles))
            CardGroup(CardGroupType.STRAIGHT, cards, singles.last())
        else null
    }

    /**
     * 连对：≥3 对连续，不能含 2 和王
     */
    private fun parseDoubleStraight(
        cards: List<Card>,
        byCount: Map<Int, List<Int>>,
        freq: Map<Int, Int>
    ): CardGroup? {
        if (cards.size < 6 || cards.size % 2 != 0) return null
        val pairs = byCount[2] ?: return null
        if (pairs.size * 2 != cards.size) return null
        if (pairs.any { it >= 15 }) return null
        return if (isConsecutive(pairs))
            CardGroup(CardGroupType.DOUBLE_STRAIGHT, cards, pairs.last())
        else null
    }

    /**
     * 飞机不带翅膀：≥2 组连续三张
     */
    private fun parseAirplaneNoWing(
        cards: List<Card>,
        byCount: Map<Int, List<Int>>
    ): CardGroup? {
        val triples = byCount[3] ?: return null
        if (triples.size < 2) return null
        if (triples.size * 3 != cards.size) return null
        // 飞机不能含 2
        if (triples.any { it >= 15 }) return null
        return if (isConsecutive(triples))
            CardGroup(CardGroupType.AIRPLANE, cards, triples.last())
        else null
    }

    /**
     * 飞机带单翅膀：N 组连续三张 + N 张单牌
     */
    private fun parseAirplaneSingle(
        cards: List<Card>,
        freq: Map<Int, Int>
    ): CardGroup? {
        val triples = freq.entries.filter { it.value == 3 }.map { it.key }.sorted()
        if (triples.size < 2) return null

        // 找出连续的三张组
        val consecutiveGroup = findLongestConsecutive(triples)
        if (consecutiveGroup.size < 2) return null

        val n = consecutiveGroup.size
        val expectedTotal = n * 3 + n  // 3N + N 翅膀
        if (cards.size != expectedTotal) return null

        // 不能带 2 和王（16,17）
        val singles = freq.entries.filter { it.value == 1 }.map { it.key }
        // 单翅膀中也不能有 2 和王级别的，但这里允许一些灵活性
        // 严格规则：带的单牌可以是任意牌（包括 2 和王）

        if (singles.size >= n) {
            return CardGroup(CardGroupType.AIRPLANE_SINGLE, cards, consecutiveGroup.last())
        }
        return null
    }

    /**
     * 飞机带双翅膀：N 组连续三张 + N 对
     */
    private fun parseAirplanePair(
        cards: List<Card>,
        freq: Map<Int, Int>
    ): CardGroup? {
        val triples = freq.entries.filter { it.value == 3 }.map { it.key }.sorted()
        if (triples.size < 2) return null

        val consecutiveGroup = findLongestConsecutive(triples)
        if (consecutiveGroup.size < 2) return null

        val n = consecutiveGroup.size
        val expectedTotal = n * 3 + n * 2  // 3N + 2N 翅膀
        if (cards.size != expectedTotal) return null

        val pairs = freq.entries.filter { it.value == 2 }.map { it.key }
        if (pairs.size >= n) {
            return CardGroup(CardGroupType.AIRPLANE_PAIR, cards, consecutiveGroup.last())
        }
        return null
    }

    /**
     * 四带二：4 张相同 + 2 张单牌（不能带王）或 4+2+2
     */
    private fun parseFourTwo(
        cards: List<Card>,
        byCount: Map<Int, List<Int>>
    ): CardGroup? {
        if (cards.size != 6 && cards.size != 8) return null
        val fours = byCount[4] ?: return null
        if (fours.size != 1) return null  // 只有一组 4 张
        return CardGroup(CardGroupType.FOUR_TWO, cards, fours[0])
    }

    /**
     * 炸弹：4 张相同
     */
    private fun parseBomb(cards: List<Card>): CardGroup? {
        if (cards.size != 4) return null
        val ranks = cards.map { it.rank }.distinct()
        if (ranks.size != 1) return null
        return CardGroup(CardGroupType.BOMB, cards, ranks[0])
    }

    /**
     * 火箭：大小王
     */
    private fun parseRocket(cards: List<Card>): CardGroup? {
        if (cards.size != 2) return null
        if (!cards[0].isJoker || !cards[1].isJoker) return null
        if (cards[0].rank == cards[1].rank) return null  // 不能两张相同王
        return CardGroup(CardGroupType.ROCKET, cards, 18)  // 火箭 keyRank 最大
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /** 判断整数列表是否连续 */
    private fun isConsecutive(sorted: List<Int>): Boolean {
        if (sorted.size < 2) return true
        return sorted.zipWithNext().all { (a, b) -> b - a == 1 }
    }

    /** 在列表中找最长连续子序列 */
    private fun findLongestConsecutive(sorted: List<Int>): List<Int> {
        if (sorted.isEmpty()) return emptyList()
        var bestStart = 0
        var bestLen = 1
        var curStart = 0
        for (i in 1 until sorted.size) {
            if (sorted[i] - sorted[i - 1] == 1) {
                if (i - curStart + 1 > bestLen) {
                    bestLen = i - curStart + 1
                    bestStart = curStart
                }
            } else {
                curStart = i
            }
        }
        return sorted.subList(bestStart, bestStart + bestLen)
    }
}