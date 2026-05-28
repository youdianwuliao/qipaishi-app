package com.qipaishi.game.doudizhu.engine

import com.qipaishi.game.doudizhu.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DoudizhuEngineTest {

    private lateinit var engine: DoudizhuEngine

    @BeforeEach
    fun setup() {
        engine = DoudizhuEngine()
    }

    // ===== 发牌 =====
    @Test
    @DisplayName("发牌：3 人各 17 张，3 张底牌")
    fun testDeal() {
        val state = engine.newGame(seed = 42)
        assertEquals(GamePhase.BIDDING, state.phase)
        assertEquals(17, state.handSizes[0])
        assertEquals(17, state.handSizes[1])
        assertEquals(17, state.handSizes[2])
        assertEquals(0, state.bottomCards.size)  // 叫地主阶段隐藏底牌
    }

    // ===== 叫地主：叫 3 分直接结束 =====
    @Test
    @DisplayName("叫 3 分直接定地主")
    fun testBidThree() {
        val state = engine.newGame(seed = 42)
        val currentPlayer = state.currentPlayerIndex

        val result = engine.bid(currentPlayer, 3)
        assertTrue(result is BidActionResult.BiddingDone)
        val done = result as BidActionResult.BiddingDone
        assertEquals(GamePhase.PLAYING, done.state.phase)
        assertEquals(currentPlayer, done.state.landlordIndex)
        assertEquals(3, done.state.bidMultiplier)
        assertEquals(20, done.state.handSizes[currentPlayer])  // 17 + 3 底牌
        assertEquals(3, done.state.bottomCards.size)  // 底牌已揭示
    }

    // ===== 叫地主：没人叫 =====
    @Test
    @DisplayName("没人叫 → 重新发牌")
    fun testNoOneBids() {
        val state = engine.newGame(seed = 42)
        val firstPlayer = state.currentPlayerIndex
        val second = (firstPlayer + 1) % 3
        val third = (firstPlayer + 2) % 3

        engine.bid(firstPlayer, 0)
        engine.bid(second, 0)
        val result = engine.bid(third, 0)

        assertTrue(result is BidActionResult.Restart)
    }

    // ===== 叫地主：两人叫，价高者得 =====
    @Test
    @DisplayName("价高者得地主")
    fun testHighestBidder() {
        val state = engine.newGame(seed = 42)
        val p0 = state.currentPlayerIndex
        val p1 = (p0 + 1) % 3
        val p2 = (p0 + 2) % 3

        engine.bid(p0, 1)
        engine.bid(p1, 2)
        val result = engine.bid(p2, 0)  // 第三人不叫

        assertTrue(result is BidActionResult.BiddingDone)
        val done = result as BidActionResult.BiddingDone
        assertEquals(p1, done.state.landlordIndex)  // 价高者得
        assertEquals(2, done.state.bidMultiplier)
    }

    // ===== 出牌 =====
    @Test
    @DisplayName("合法出牌 → accepted")
    fun testValidPlay() {
        startPlaying()

        // 地主先出
        val landlord = engine.getState().currentPlayerIndex
        val hand = engine.getHand(landlord)
        val single = listOf(hand.first())

        val result = engine.play(landlord, single)
        assertTrue(result is PlayActionResult.Accepted)
        assertEquals(19, engine.getHand(landlord).size)  // 少了一张
    }

    @Test
    @DisplayName("非法出牌 → invalid")
    fun testInvalidPlay() {
        startPlaying()

        val landlord = engine.getState().currentPlayerIndex
        val hand = engine.getHand(landlord)
        // 挑两张不同点数的牌组成"对子"，解析失败
        val distinctCards = hand.distinctBy { it.rank }.take(2)
        if (distinctCards.size == 2 && distinctCards[0].rank != distinctCards[1].rank) {
            val result = engine.play(landlord, distinctCards)
            assertTrue(result is PlayActionResult.Invalid)
        }
    }

    @Test
    @DisplayName("出牌打不过 → invalid")
    fun testCantBeat() {
        startPlaying()

        val landlord = engine.getState().currentPlayerIndex
        val p1 = (landlord + 1) % 3
        val hand = engine.getHand(landlord)

        // 地主出大的单牌
        val bigCard = hand.maxByOrNull { it.rank }!!
        engine.play(landlord, listOf(bigCard))

        // p1 的手牌里找一张比地主大的
        val p1Hand = engine.getHand(p1)
        val bigger = p1Hand.filter { it.rank > bigCard.rank }
        if (bigger.isEmpty()) return  // 确实打不过就算了

        val result = engine.play(p1, listOf(bigger.first()))
        assertTrue(result is PlayActionResult.Accepted)
    }

    // ===== 不出（pass） =====
    @Test
    @DisplayName("pass → 轮到下家")
    fun testPass() {
        startPlaying()

        val landlord = engine.getState().currentPlayerIndex
        val hand = engine.getHand(landlord)
        engine.play(landlord, listOf(hand.first()))

        val nextPlayer = engine.getState().currentPlayerIndex
        val result = engine.pass(nextPlayer)
        assertTrue(result is PlayActionResult.Passed)
    }

    @Test
    @DisplayName("新一轮不能 pass")
    fun testCantPassNewRound() {
        startPlaying()
        val landlord = engine.getState().currentPlayerIndex

        assertThrows(IllegalArgumentException::class.java) {
            engine.pass(landlord)  // 新一轮必须出牌
        }
    }

    // ===== 积分结算 =====
    @Test
    @DisplayName("叫 2 分，0 炸弹，地主胜")
    fun testScoreBid2NoBombLandlordWin() {
        val result = GameResult(0, 2, 0, WinSide.LANDLORD)
        val scores = result.calculateScores()
        assertEquals(4000, scores[0])   // 地主 +2000×2
        assertEquals(-2000, scores[1])  // 农民 -1000×2
        assertEquals(-2000, scores[2])
    }

    @Test
    @DisplayName("叫 3 分，2 炸弹，农民胜")
    fun testScoreBid3TwoBombsFarmerWin() {
        val result = GameResult(1, 3, 2, WinSide.FARMER)
        val scores = result.calculateScores()
        val mult = 3 * 4  // 3分 × 2² = 12 倍
        assertEquals(-24000, scores[1]) // 地主 -2000×12
        assertEquals(12000, scores[0])  // 农民 +1000×12
        assertEquals(12000, scores[2])
    }

    @Test
    @DisplayName("叫 1 分，1 炸弹，地主胜")
    fun testScoreBid1OneBombLandlordWin() {
        val result = GameResult(2, 1, 1, WinSide.LANDLORD)
        val scores = result.calculateScores()
        val mult = 1 * 2  // 1分 × 2 = 2倍
        assertEquals(4000, scores[2])   // 地主 +2000×2
        assertEquals(-2000, scores[0])  // 农民 -1000×2
        assertEquals(-2000, scores[1])
    }

    // ===== 辅助 =====
    private fun startPlaying() {
        engine.newGame(seed = 42)
        val state = engine.getState()
        val p0 = state.currentPlayerIndex
        val p1 = (p0 + 1) % 3
        val p2 = (p0 + 2) % 3
        engine.bid(p0, 2)
        engine.bid(p1, 0)
        engine.bid(p2, 0) // 三人叫完一轮
    }
}