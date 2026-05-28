package com.qipaishi.game.doudizhu.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * CardGroupParser 牌型判断测试
 */
class CardGroupParserTest {

    private fun c(rank: Int, suit: Card.Suit = Card.Suit.SPADE) =
        Card.of(suit, rank)

    // ===== 单张 =====
    @Test
    @DisplayName("单张：♠3")
    fun testSingle() {
        val group = CardGroupParser.parse(listOf(c(3)))
        assertNotNull(group)
        assertEquals(CardGroupType.SINGLE, group!!.type)
        assertEquals(3, group.keyRank)
    }

    // ===== 对子 =====
    @Test
    @DisplayName("对子：♠3 + ♥3")
    fun testPair() {
        val group = CardGroupParser.parse(listOf(
            Card.of(Card.Suit.SPADE, 3),
            Card.of(Card.Suit.HEART, 3)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.PAIR, group!!.type)
        assertEquals(3, group.keyRank)
    }

    @Test
    @DisplayName("对子：不同点数 → null")
    fun testPairInvalid() {
        val group = CardGroupParser.parse(listOf(c(3), c(4)))
        assertNull(group)  // 不解析为对子（顺子也是 ≥5 张才行）
    }

    // ===== 三不带 =====
    @Test
    @DisplayName("三不带：3 张 5")
    fun testTriple() {
        val group = CardGroupParser.parse(listOf(
            Card.of(Card.Suit.SPADE, 5),
            Card.of(Card.Suit.HEART, 5),
            Card.of(Card.Suit.CLUB, 5)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.TRIPLE, group!!.type)
        assertEquals(5, group.keyRank)
    }

    // ===== 三带一 =====
    @Test
    @DisplayName("三带一：3 张 7 + 1 张 2")
    fun testTripleOne() {
        val group = CardGroupParser.parse(listOf(
            Card.of(Card.Suit.SPADE, 7),
            Card.of(Card.Suit.HEART, 7),
            Card.of(Card.Suit.CLUB, 7),
            Card.of(Card.Suit.DIAMOND, 2)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.TRIPLE_ONE, group!!.type)
        assertEquals(7, group.keyRank)
    }

    // ===== 三带二 =====
    @Test
    @DisplayName("三带二：3 张 9 + 1 对 4")
    fun testTripleTwo() {
        val group = CardGroupParser.parse(listOf(
            Card.of(Card.Suit.SPADE, 9),
            Card.of(Card.Suit.HEART, 9),
            Card.of(Card.Suit.CLUB, 9),
            Card.of(Card.Suit.DIAMOND, 4),
            Card.of(Card.Suit.CLUB, 4)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.TRIPLE_TWO, group!!.type)
    }

    // ===== 顺子 =====
    @Test
    @DisplayName("顺子：3-4-5-6-7")
    fun testStraight5() {
        val group = CardGroupParser.parse(listOf(
            c(3), c(4), c(5), c(6), c(7)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.STRAIGHT, group!!.type)
        assertEquals(7, group.keyRank)
    }

    @Test
    @DisplayName("顺子：4 张不合法")
    fun testStraightTooShort() {
        val group = CardGroupParser.parse(listOf(c(3), c(4), c(5), c(6)))
        assertNull(group)
    }

    @Test
    @DisplayName("顺子：含 2 不合法")
    fun testStraightWithTwo() {
        val group = CardGroupParser.parse(listOf(c(12), c(13), c(14), c(15), c(16)))
        assertNull(group)
    }

    @Test
    @DisplayName("顺子：不连续不合法")
    fun testStraightNotConsecutive() {
        val group = CardGroupParser.parse(listOf(c(3), c(4), c(6), c(7), c(8)))
        assertNull(group)
    }

    // ===== 连对 =====
    @Test
    @DisplayName("连对：33-44-55")
    fun testDoubleStraight() {
        val group = CardGroupParser.parse(listOf(
            c(3, Card.Suit.SPADE), c(3, Card.Suit.HEART),
            c(4, Card.Suit.SPADE), c(4, Card.Suit.HEART),
            c(5, Card.Suit.SPADE), c(5, Card.Suit.HEART)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.DOUBLE_STRAIGHT, group!!.type)
        assertEquals(5, group.keyRank)
    }

    // ===== 炸弹 =====
    @Test
    @DisplayName("炸弹：4 张 A")
    fun testBomb() {
        val group = CardGroupParser.parse(listOf(
            c(14, Card.Suit.SPADE), c(14, Card.Suit.HEART),
            c(14, Card.Suit.CLUB), c(14, Card.Suit.DIAMOND)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.BOMB, group!!.type)
        assertEquals(14, group.keyRank)
    }

    @Test
    @DisplayName("炸弹：3 张相同不是炸弹")
    fun testNotBomb() {
        val group = CardGroupParser.parse(listOf(c(5), c(5), c(5), c(6)))
        assertEquals(CardGroupType.TRIPLE_ONE, group!!.type)  // 应该识别为三带一
    }

    // ===== 火箭 =====
    @Test
    @DisplayName("火箭：大小王")
    fun testRocket() {
        val group = CardGroupParser.parse(listOf(Card.bigJoker(), Card.smallJoker()))
        assertNotNull(group)
        assertEquals(CardGroupType.ROCKET, group!!.type)
        assertEquals(18, group.keyRank)
    }

    // ===== 飞机 =====
    @Test
    @DisplayName("飞机不带：333-444")
    fun testAirplaneNoWing() {
        val group = CardGroupParser.parse(listOf(
            c(3, Card.Suit.SPADE), c(3, Card.Suit.HEART), c(3, Card.Suit.CLUB),
            c(4, Card.Suit.SPADE), c(4, Card.Suit.HEART), c(4, Card.Suit.CLUB)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.AIRPLANE, group!!.type)
    }

    // ===== 四带二 =====
    @Test
    @DisplayName("四带二：4 张 K + 2 张单")
    fun testFourTwo() {
        val group = CardGroupParser.parse(listOf(
            c(13, Card.Suit.SPADE), c(13, Card.Suit.HEART),
            c(13, Card.Suit.CLUB), c(13, Card.Suit.DIAMOND),
            c(3), c(8)
        ))
        assertNotNull(group)
        assertEquals(CardGroupType.FOUR_TWO, group!!.type)
        assertEquals(13, group.keyRank)
    }
}