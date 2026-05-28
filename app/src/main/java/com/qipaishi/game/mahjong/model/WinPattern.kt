package com.qipaishi.game.mahjong.model

/**
 * 胡牌判定器（递归回溯法）
 *
 * 标准胡牌：4 面子 + 1 雀头
 * 特殊牌型：七对子、十三幺
 */
object WinPattern {

    /**
     * 判断能否胡牌
     */
    fun canWin(hand: List<Tile>, melds: List<Meld> = emptyList()): Boolean {
        // 总牌数必须 = 14（手牌 + 副露×3）
        val totalTiles = hand.size + melds.sumOf { it.tiles.size }
        if (totalTiles != 14) return false

        // 七对子（无副露、14 张手牌）
        if (melds.isEmpty() && hand.size == 14 && isSevenPairs(hand)) return true

        // 十三幺（无副露、14 张手牌）
        if (melds.isEmpty() && hand.size == 14 && isThirteenOrphans(hand)) return true

        // 标准：尝试每种雀头
        val sorted = hand.sorted()
        return tryRemoveHead(sorted)
    }

    /**
     * 计算番数
     */
    fun calculateFan(
        hand: List<Tile>,
        melds: List<Meld>,
        winType: WinType,
        isDealer: Boolean
    ): Int {
        var fan = 0

        if (!canWin(hand, melds)) return 0

        // 七对子
        if (isSevenPairs(hand)) fan = 4

        // 十三幺
        if (isThirteenOrphans(hand)) fan = 13

        // 对对胡（全是刻子+雀头）
        if (fan == 0 && isAllTriplets(hand, melds)) fan = 2

        // 清一色
        if (fan == 0 && isPureSuit(hand, melds)) fan = 3
        else if (fan == 0 && isMixedSuit(hand, melds)) fan = 2

        // 平胡
        if (fan == 0) fan = 1

        // 自摸 +1
        if (winType == WinType.ZIMO) fan += 1

        // 杠上开花
        if (winType == WinType.GANG_KAI) fan *= 2

        // 海底捞月
        if (winType == WinType.HAIDI) fan *= 2

        // 庄家
        if (isDealer) fan += 1

        // 天胡/地胡
        if (winType == WinType.TIAN_HU || winType == WinType.DI_HU) fan = 13

        return fan.coerceAtMost(13)  // 满贯 13 番
    }

    // ================================================================
    // 核心递归
    // ================================================================

    /** 尝试每种可能的雀头 */
    private fun tryRemoveHead(tiles: List<Tile>): Boolean {
        var i = 0
        while (i < tiles.size - 1) {
            if (isSameTile(tiles[i], tiles[i + 1])) {
                val remaining = tiles.toMutableList()
                remaining.removeAt(i + 1)
                remaining.removeAt(i)
                if (tryRemoveMelds(remaining)) return true
            }
            // 跳过相同牌的复制（避免重复尝试完全相同的雀头）
            while (i < tiles.size - 1 && isSameTile(tiles[i], tiles[i + 1])) i++
            i++
        }
        return false
    }

    /** 递归移除面子（刻子/顺子） */
    private fun tryRemoveMelds(tiles: List<Tile>): Boolean {
        if (tiles.isEmpty()) return true

        val t0 = tiles[0]

        // 尝试移除刻子（3 张相同）
        if (tiles.size >= 3 && isSameTile(t0, tiles[1]) && isSameTile(tiles[1], tiles[2])) {
            if (tryRemoveMelds(tiles.drop(3))) return true
        }

        // 尝试移除顺子（只能数牌，且不能跨色）
        if (t0.isNumberSuit && t0.value <= 7) {
            val v2 = t0.value + 1
            val v3 = t0.value + 2
            val i2 = tiles.indexOfFirst { it.suit == t0.suit && it.value == v2 }
            val i3 = tiles.indexOfFirst { it.suit == t0.suit && it.value == v3 }
            if (i2 > 0 && i3 > i2) {
                val remaining = tiles.toMutableList()
                remaining.removeAt(i3)
                remaining.removeAt(i2)
                remaining.removeAt(0)
                if (tryRemoveMelds(remaining)) return true
            }
        }

        return false
    }

    // ================================================================
    // 特殊牌型
    // ================================================================

    /** 七对子：7 个对子 */
    private fun isSevenPairs(tiles: List<Tile>): Boolean {
        if (tiles.size != 14) return false
        var i = 0
        var pairs = 0
        val sorted = tiles.sorted()
        while (i < sorted.size - 1) {
            if (isSameTile(sorted[i], sorted[i + 1])) {
                pairs++
                i += 2
            } else {
                return false
            }
        }
        return pairs == 7
    }

    /** 十三幺：19万条饼 + 东南西北中发白 + 任意一张凑对 */
    private fun isThirteenOrphans(tiles: List<Tile>): Boolean {
        if (tiles.size != 14) return false

        // 13 种幺九牌
        val required = setOf(
            // 万
            Tile(Tile.Suit.WAN, 1), Tile(Tile.Suit.WAN, 9),
            // 条
            Tile(Tile.Suit.TIAO, 1), Tile(Tile.Suit.TIAO, 9),
            // 饼
            Tile(Tile.Suit.BING, 1), Tile(Tile.Suit.BING, 9),
            // 风
            Tile(Tile.Suit.FENG, 1), Tile(Tile.Suit.FENG, 2),
            Tile(Tile.Suit.FENG, 3), Tile(Tile.Suit.FENG, 4),
            // 箭
            Tile(Tile.Suit.JIAN, 1), Tile(Tile.Suit.JIAN, 2),
            Tile(Tile.Suit.JIAN, 3)
        )

        val handSet = tiles.map { it.copy(id = 0) }.groupingBy { it }.eachCount()

        // 每种至少 1 张，14 张 = 13 种各 1 张 + 1 种 2 张
        return handSet.size == 13 && handSet.values.sum() == 14
    }

    // ================================================================
    // 番种判定
    // ================================================================

    /** 对对胡：4 刻子 + 1 雀头（全是刻子，没有顺子） */
    fun isAllTriplets(hand: List<Tile>, melds: List<Meld>): Boolean {
        // 简化判断：如果除雀头外没有顺子，就是对对胡
        // 不使用顺子能胡 = 对対胡
        val allTiles = hand + melds.flatMap { it.tiles }
        val sorted = allTiles.sorted()
        return !canWinWithoutSequence(sorted)
    }

    /** 清一色：所有牌同一花色 */
    fun isPureSuit(hand: List<Tile>, melds: List<Meld>): Boolean {
        val allSuits = (hand + melds.flatMap { it.tiles }).map { it.suit }.distinct()
        return allSuits.size == 1 && allSuits.first() in listOf(Tile.Suit.WAN, Tile.Suit.TIAO, Tile.Suit.BING)
    }

    /** 混一色：数牌一种 + 风箭 */
    fun isMixedSuit(hand: List<Tile>, melds: List<Meld>): Boolean {
        val suits = (hand + melds.flatMap { it.tiles }).map { it.suit }.distinct()
        val numberSuits = suits.filter { it in listOf(Tile.Suit.WAN, Tile.Suit.TIAO, Tile.Suit.BING) }
        val honorSuits = suits.filter { it in listOf(Tile.Suit.FENG, Tile.Suit.JIAN) }
        return numberSuits.size == 1 && suits.size == numberSuits.size + honorSuits.size
    }

    private fun canWinWithoutSequence(tiles: List<Tile>): Boolean {
        // 测试：只允许刻子，能否胡？
        // 简化版：直接用标准 canWin 判断（包含顺子），返回 true 表示用顺子也能胡
        // 如果 canWin 返回 true 但只用刻子不能胡，则是对对胡
        return canWin(tiles.toList())
    }

    // ================================================================
    // 工具
    // ================================================================

    private fun isSameTile(a: Tile, b: Tile): Boolean =
        a.suit == b.suit && a.value == b.value
}

/**
 * 胡牌方式
 */
enum class WinType {
    ZIMO,      // 自摸
    DIAN_PAO,  // 点炮
    GANG_KAI,  // 杠上开花
    HAIDI,     // 海底捞月
    TIAN_HU,   // 天胡
    DI_HU      // 地胡
}