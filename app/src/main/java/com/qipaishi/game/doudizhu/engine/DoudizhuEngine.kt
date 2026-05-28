package com.qipaishi.game.doudizhu.engine

import com.qipaishi.game.doudizhu.model.*

/**
 * 游戏阶段
 */
enum class GamePhase {
    /** 等待叫地主 */
    BIDDING,
    /** 出牌中 */
    PLAYING,
    /** 游戏结束 */
    GAME_OVER
}

/**
 * 叫地主结果
 */
data class BidResult(
    val playerIndex: Int,
    val score: Int  // 0=不叫, 1/2/3=叫分
)

/**
 * 游戏状态快照（发送给客户端）
 */
data class GameState(
    val phase: GamePhase,
    val currentPlayerIndex: Int,           // 当前轮到谁
    val landlordIndex: Int,                // 地主是谁（-1=未确定）
    val hands: List<List<Card>>,           // 各玩家手牌
    val handSizes: List<Int>,              // 各玩家手牌数（对手只发数量）
    val bottomCards: List<Card>,           // 底牌（叫地主阶段隐藏）
    val lastPlay: PlayedCards?,            // 上一轮出牌
    val bidMultiplier: Int,                // 叫分倍数
    val bombCount: Int,                    // 已出现的炸弹数
    val bidResults: List<BidResult>,       // 叫分历史
    val winner: Int?                       // 胜者（-2=农民胜, 否则=地主下标）
)

/**
 * 一轮出牌记录
 */
data class PlayedCards(
    val playerIndex: Int,
    val group: CardGroup,
    val isNewRound: Boolean  // 是否新一轮（上两家都 pass 了）
)

/**
 * 斗地主游戏引擎
 *
 * 纯逻辑层，不涉及网络和 UI，可直接单元测试。
 *
 * 使用方式：
 * 1. newGame() — 发牌
 * 2. bid(playerIndex, score) — 叫地主（轮询直到有人叫或重发）
 * 3. play(playerIndex, cards) — 出牌
 * 4. pass(playerIndex) — 不出
 * 5. 检查 gameOver → 结算
 */
class DoudizhuEngine {

    // ===== 内部状态 =====
    private var deck = CardDeck.full()
    private var hands = mutableListOf<MutableList<Card>>()
    private var bottomCards = listOf<Card>()
    private var phase = GamePhase.BIDDING
    private var landlordIndex = -1
    private var currentPlayerIndex = 0
    private var bidMultiplier = 1
    private var bombCount = 0
    private var lastPlay: PlayedCards? = null
    private var passCount = 0
    private var bidResults = mutableListOf<BidResult>()
    private var bidCurrentIndex = 0
    private var highestBid = 0
    private var highestBidderIndex = -1

    // ===== 公开方法 =====

    /** 开始新游戏 */
    fun newGame(seed: Long? = null): GameState {
        if (seed != null) {
            // 使用固定种子便于测试
            kotlin.random.Random(seed)
        }

        val dealResult = CardDeck.deal()
        hands = dealResult.hands.map { it.toMutableList() }.toMutableList()
        bottomCards = dealResult.bottom

        phase = GamePhase.BIDDING
        landlordIndex = -1
        currentPlayerIndex = (0..2).random()
        bidMultiplier = 1
        bombCount = 0
        lastPlay = null
        passCount = 0
        bidResults.clear()
        bidCurrentIndex = currentPlayerIndex
        highestBid = 0
        highestBidderIndex = -1

        return buildState(hideBottom = true)
    }

    /** 叫地主 */
    fun bid(playerIndex: Int, score: Int): BidActionResult {
        require(phase == GamePhase.BIDDING) { "当前不在叫地主阶段" }
        require(playerIndex == bidCurrentIndex) { "不是你的回合" }
        require(score in 0..3) { "叫分必须在 0~3 之间" }

        bidResults.add(BidResult(playerIndex, score))

        if (score > highestBid) {
            highestBid = score
            highestBidderIndex = playerIndex
        }

        // 叫分 3 直接结束叫地主
        if (score == 3) {
            return finalizeBidding(highestBidderIndex)
        }

        // 所有人叫完一轮
        bidCurrentIndex = (bidCurrentIndex + 1) % 3
        if (bidResults.size == 3) {
            if (highestBid == 0) {
                // 没人叫 → 重新发牌
                return BidActionResult.restart(buildState(hideBottom = true))
            }
            return finalizeBidding(highestBidderIndex)
        }

        return BidActionResult.continueBidding(buildState(hideBottom = true))
    }

    /** 出牌 */
    fun play(playerIndex: Int, rawCards: List<Card>): PlayActionResult {
        require(phase == GamePhase.PLAYING) { "当前不在出牌阶段" }
        require(playerIndex == currentPlayerIndex) { "不是你的回合" }

        // 1. 解析牌型
        val group = CardGroupParser.parse(rawCards)
            ?: return PlayActionResult.invalid("无效牌型")

        // 2. 验证手牌中确实有这些牌
        val hand = hands[playerIndex]
        val handRankCount = hand.groupBy { it.rank }.mapValues { it.value.size }
        val playRankCount = rawCards.groupBy { it.rank }.mapValues { it.value.size }
        for ((rank, count) in playRankCount) {
            if ((handRankCount[rank] ?: 0) < count) {
                return PlayActionResult.invalid("手牌中没有这些牌")
            }
        }

        // 3. 比较大小
        if (!CardComparator.canBeat(group, lastPlay?.group)) {
            return PlayActionResult.invalid("打不过上一轮的牌")
        }

        // 4. 从手牌移除
        for (card in rawCards) {
            hand.remove(card)
        }

        // 5. 记录炸弹
        if (group.type == CardGroupType.BOMB || group.type == CardGroupType.ROCKET) {
            bombCount++
        }

        // 6. 更新状态
        lastPlay = PlayedCards(playerIndex, group, passCount >= 2)
        passCount = 0

        // 7. 检查是否出完了
        if (hand.isEmpty()) {
            return finalizeGame(playerIndex)
        }

        // 8. 轮到下家
        currentPlayerIndex = (currentPlayerIndex + 1) % 3
        return PlayActionResult.accepted(buildState())
    }

    /** 不出（pass） */
    fun pass(playerIndex: Int): PlayActionResult {
        require(phase == GamePhase.PLAYING) { "当前不在出牌阶段" }
        require(playerIndex == currentPlayerIndex) { "不是你的回合" }
        require(lastPlay != null) { "新一轮不能 pass，必须出牌" }

        passCount++
        currentPlayerIndex = (currentPlayerIndex + 1) % 3

        // 连续两家 pass → 新一轮
        if (passCount >= 2) {
            lastPlay = null
            passCount = 0
        }

        return PlayActionResult.passed(buildState())
    }

    /** 获取当前完整状态 */
    fun getState(): GameState = buildState()

    /** 获取某个玩家的完整手牌（仅该玩家自己能看到） */
    fun getHand(playerIndex: Int): List<Card> = hands[playerIndex].toList()

    // ===== 私有方法 =====

    private fun finalizeBidding(winnerIndex: Int): BidActionResult {
        landlordIndex = winnerIndex
        bidMultiplier = highestBid.coerceAtLeast(1)

        // 地主拿底牌
        hands[landlordIndex].addAll(bottomCards)
        hands[landlordIndex].sort()

        phase = GamePhase.PLAYING
        currentPlayerIndex = landlordIndex
        lastPlay = null
        passCount = 0

        return BidActionResult.biddingDone(buildState(showBottom = true))
    }

    private fun finalizeGame(winnerIndex: Int): PlayActionResult {
        phase = GamePhase.GAME_OVER
        val winSide = if (winnerIndex == landlordIndex) -1 else -2  // -1=地主胜, -2=农民胜

        val result = GameResult(
            landlordIndex = landlordIndex,
            bidMultiplier = bidMultiplier,
            bombCount = bombCount,
            winnerSide = if (winSide == -1) WinSide.LANDLORD else WinSide.FARMER
        )

        return PlayActionResult.gameOver(buildState(winner = winSide), result)
    }

    private fun buildState(
        hideBottom: Boolean = false,
        showBottom: Boolean = false,
        winner: Int? = null
    ): GameState {
        return GameState(
            phase = phase,
            currentPlayerIndex = currentPlayerIndex,
            landlordIndex = landlordIndex,
            hands = if (phase == GamePhase.GAME_OVER) hands.map { it.toList() } else hands.map { it.toList() },
            handSizes = hands.map { it.size },
            bottomCards = when {
                phase == GamePhase.PLAYING || phase == GamePhase.GAME_OVER -> bottomCards
                hideBottom -> emptyList()
                else -> bottomCards
            },
            lastPlay = lastPlay,
            bidMultiplier = bidMultiplier,
            bombCount = bombCount,
            bidResults = bidResults.toList(),
            winner = winner
        )
    }
}

// ================================================================
// 结果类型
// ================================================================

sealed class BidActionResult {
    data class ContinueBidding(val state: GameState) : BidActionResult()
    data class BiddingDone(val state: GameState) : BidActionResult()
    data class Restart(val state: GameState) : BidActionResult()

    companion object {
        fun continueBidding(state: GameState) = ContinueBidding(state)
        fun biddingDone(state: GameState) = BiddingDone(state)
        fun restart(state: GameState) = Restart(state)
    }
}

sealed class PlayActionResult {
    data class Accepted(val state: GameState) : PlayActionResult()
    data class Passed(val state: GameState) : PlayActionResult()
    data class GameOver(val state: GameState, val result: GameResult) : PlayActionResult()
    data class Invalid(val reason: String) : PlayActionResult()

    companion object {
        fun accepted(state: GameState) = Accepted(state)
        fun passed(state: GameState) = Passed(state)
        fun gameOver(state: GameState, result: GameResult) = GameOver(state, result)
        fun invalid(reason: String) = Invalid(reason)
    }
}

enum class WinSide { LANDLORD, FARMER }

data class GameResult(
    val landlordIndex: Int,
    val bidMultiplier: Int,
    val bombCount: Int,
    val winnerSide: WinSide
) {
    val finalMultiplier: Int get() = bidMultiplier * (1 shl bombCount)

    fun calculateScores(): Map<Int, Int> {
        val base = 1000 * finalMultiplier
        return when (winnerSide) {
            WinSide.LANDLORD -> mapOf(
                landlordIndex to base * 2,
                (landlordIndex + 1) % 3 to -base,
                (landlordIndex + 2) % 3 to -base
            )
            WinSide.FARMER -> mapOf(
                landlordIndex to -base * 2,
                (landlordIndex + 1) % 3 to base,
                (landlordIndex + 2) % 3 to base
            )
        }
    }
}