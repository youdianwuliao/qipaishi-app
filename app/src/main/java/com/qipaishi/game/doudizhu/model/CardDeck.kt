package com.qipaishi.game.doudizhu.model

/**
 * 牌堆
 */
object CardDeck {

    /** 生成完整 54 张牌 */
    fun full(): List<Card> {
        val cards = mutableListOf<Card>()
        for (suit in listOf(Card.Suit.SPADE, Card.Suit.HEART, Card.Suit.CLUB, Card.Suit.DIAMOND)) {
            for (rank in 3..15) {
                cards.add(Card.of(suit, rank))
            }
        }
        cards.add(Card.smallJoker())
        cards.add(Card.bigJoker())
        return cards
    }

    /** 洗牌（Fisher-Yates） */
    fun shuffle(cards: List<Card>): List<Card> {
        val shuffled = cards.toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = (0..i).random()
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }
        return shuffled
    }

    /** 发牌：洗牌 → 每人 17 张 → 3 张底牌 */
    fun deal(): DealResult {
        val shuffled = shuffle(full())
        val hands = listOf(
            shuffled.subList(0, 17).sorted(),       // 玩家 0
            shuffled.subList(17, 34).sorted(),       // 玩家 1
            shuffled.subList(34, 51).sorted()        // 玩家 2
        )
        val bottom = shuffled.subList(51, 54).sorted()
        return DealResult(hands, bottom)
    }

    data class DealResult(
        val hands: List<List<Card>>,   // 3 份手牌
        val bottom: List<Card>         // 3 张底牌
    )
}