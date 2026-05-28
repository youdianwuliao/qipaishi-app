package com.qipaishi.game.mahjong.model

/**
 * 麻将牌
 *
 * @param suit 花色：万/条/饼/风/箭
 * @param value 1-9（数牌）/ 1-4（东南西北）/ 1-3（中发白）
 * @param id 唯一标识（同花色同值的 4 张牌用不同 id 区分）
 */
data class Tile(
    val suit: Suit,
    val value: Int,
    val id: Int = 0  // 0-based 副数（0-3，区分 4 张相同牌）
) : Comparable<Tile> {

    enum class Suit(val symbol: String, val displayName: String) {
        WAN("万", "万"),       // 1-9 万
        TIAO("条", "条"),      // 1-9 条
        BING("饼", "饼"),     // 1-9 饼
        FENG("风", "风"),      // 1-4 东南西北
        JIAN("箭", "箭")       // 1-3 中发白
    }

    /** 友好显示 */
    val display: String
        get() = when (suit) {
            Suit.WAN -> "${value}万"
            Suit.TIAO -> "${value}条"
            Suit.BING -> "${value}饼"
            Suit.FENG -> when (value) {
                1 -> "东"
                2 -> "南"
                3 -> "西"
                4 -> "北"
                else -> "?"
            }
            Suit.JIAN -> when (value) {
                1 -> "中"
                2 -> "发"
                3 -> "白"
                else -> "?"
            }
        }

    val isNumberSuit: Boolean
        get() = suit in listOf(Suit.WAN, Suit.TIAO, Suit.BING)

    /** 是否为幺九牌（1、9、风、箭）— 用于十三幺判断 */
    val isTerminalOrHonor: Boolean
        get() = when (suit) {
            Suit.WAN, Suit.TIAO, Suit.BING -> value == 1 || value == 9
            Suit.FENG, Suit.JIAN -> true
        }

    override fun compareTo(other: Tile): Int {
        val suitDiff = this.suit.ordinal - other.suit.ordinal
        if (suitDiff != 0) return suitDiff
        val valueDiff = this.value - other.value
        if (valueDiff != 0) return valueDiff
        return this.id - other.id
    }
}