package com.qipaishi.game.doudizhu.model

/**
 * 斗地主扑克牌
 *
 * @param suit 花色
 * @param rank 点数：3=3, 4=4, ..., K=13, A=14, 2=15, 小王=16, 大王=17
 */
data class Card(
    val suit: Suit,
    val rank: Int
) : Comparable<Card> {

    enum class Suit(val symbol: String, val order: Int) {
        SPADE("♠", 4),
        HEART("♥", 3),
        CLUB("♣", 2),
        DIAMOND("♦", 1),
        JOKER("🃏", 0)  // 大小王用这个
    }

    val display: String
        get() = when {
            suit == Suit.JOKER && rank == 16 -> "🃏小"
            suit == Suit.JOKER && rank == 17 -> "🃏大"
            else -> "${suit.symbol}${rankDisplay}"
        }

    private val rankDisplay: String
        get() = when (rank) {
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            14 -> "A"
            15 -> "2"
            else -> rank.toString()
        }

    val isJoker: Boolean get() = suit == Suit.JOKER

    /**
     * 排序：先按点数，再按花色（同点数时黑>红>梅>方）
     */
    override fun compareTo(other: Card): Int {
        val rankDiff = this.rank - other.rank
        return if (rankDiff != 0) rankDiff else this.suit.order - other.suit.order
    }

    companion object {
        fun smallJoker() = Card(Suit.JOKER, 16)
        fun bigJoker() = Card(Suit.JOKER, 17)

        fun of(suit: Suit, rank: Int): Card {
            require(rank in 3..15) { "点数必须在 3~15 之间" }
            require(suit != Suit.JOKER) { "JOKER 花色仅用于大小王" }
            return Card(suit, rank)
        }
    }
}