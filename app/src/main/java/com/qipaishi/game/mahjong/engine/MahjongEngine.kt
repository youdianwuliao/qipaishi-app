package com.qipaishi.game.mahjong.engine

import com.qipaishi.game.mahjong.model.*

/**
 * 麻将游戏引擎
 *
 * 回合流：
 * 1. 庄家摸牌 → 打牌 → 等待其他玩家反应
 * 2. 其他玩家可以：胡/杠/碰/吃（优先级递减）
 * 3. 没人动作 → 下家摸牌 → 重复
 * 4. 有人吃碰杠 → 该玩家打牌 → 继续
 * 5. 胡牌 → 结算
 * 6. 流局（牌墙空）→ 庄家下庄
 */
class MahjongEngine {

    // ===== 内部状态 =====
    private var hands = mutableListOf<MutableList<Tile>>()
    private var melds = mutableListOf<MutableList<Meld>>()  // 每个玩家的副露
    private var wall = mutableListOf<Tile>()
    private var currentPlayer = 0
    private var dealerIndex = 0
    private var lastDiscard: Tile? = null
    private var lastDiscardPlayer: Int = -1
    private var phase = Phase.WAIT_DRAW
    private var drawnTile: Tile? = null  // 刚摸的牌

    enum class Phase {
        WAIT_DRAW,          // 等待摸牌
        WAIT_DISCARD,       // 等待打牌（摸完牌后）
        WAIT_RESPONSE,      // 等待其他人回应（打牌后）
        GAME_OVER,
        DRAW_GAME           // 流局
    }

    // ===== 公开方法 =====

    /** 开始新游戏 */
    fun newGame(dealerIndex: Int = 0): MahjongState {
        val dealResult = TileWall.deal()
        this.dealerIndex = dealerIndex
        hands = dealResult.hands.map { it.toMutableList() }.toMutableList()
        melds = (0..3).map { mutableListOf<Meld>() }.toMutableList()
        wall = dealResult.wall
        currentPlayer = dealerIndex
        phase = Phase.WAIT_DRAW
        lastDiscard = null
        lastDiscardPlayer = -1
        drawnTile = null

        return buildState()
    }

    /** 摸牌 */
    fun draw(playerIndex: Int): DrawResult {
        require(phase == Phase.WAIT_DRAW) { "当前不在摸牌阶段" }
        require(playerIndex == currentPlayer) { "不是你的回合" }

        val (tile, newWall) = TileWall.draw(wall)
        wall = newWall

        if (tile == null) {
            phase = Phase.DRAW_GAME
            return DrawResult.drawGame(buildState())
        }

        drawnTile = tile
        hands[playerIndex].add(tile)
        hands[playerIndex].sort()

        // 检查自摸
        if (WinPattern.canWin(hands[playerIndex].toList(), melds[playerIndex].toList())) {
            phase = Phase.GAME_OVER
            val isTianHu = (playerIndex == dealerIndex && hands.all { it.size == 14 || it.size == 13 })
            val winType = if (isTianHu) WinType.TIAN_HU else WinType.ZIMO
            val fan = WinPattern.calculateFan(
                hands[playerIndex], melds[playerIndex], winType, playerIndex == dealerIndex
            )
            return DrawResult.zimo(buildState(), playerIndex, fan)
        }

        // 检查暗杠/加杠
        val hiddenGang = MeldDetector.findHiddenGang(hands[playerIndex])
        val addedGang = melds[playerIndex].let { MeldDetector.findAddedGang(hands[playerIndex], it) }

        phase = Phase.WAIT_DISCARD
        return DrawResult.readyToDiscard(
            buildState(),
            canHiddenGang = hiddenGang != null,
            canAddedGang = addedGang != null
        )
    }

    /** 打牌 */
    fun discard(playerIndex: Int, tile: Tile): DiscardResult {
        require(phase == Phase.WAIT_DISCARD) { "当前不在打牌阶段" }
        require(playerIndex == currentPlayer) { "不是你的回合" }

        val removed = hands[playerIndex].remove(tile)
        require(removed) { "手牌中没有这张牌" }

        lastDiscard = tile
        lastDiscardPlayer = playerIndex
        drawnTile = null
        phase = Phase.WAIT_RESPONSE

        // 检查其他人能否胡/杠/碰/吃
        val responses = mutableMapOf<Int, PlayerResponses>()

        for (i in 0..3) {
            if (i == playerIndex) continue
            val canHu = WinPattern.canWin(hands[i].toList() + tile, melds[i].toList())
            val canGang = MeldDetector.canGangOpen(hands[i], tile)
            val canPeng = MeldDetector.canPeng(hands[i], tile)
            // 吃只能吃上家
            val canChi = (i == (playerIndex + 1) % 4) && MeldDetector.canChi(hands[i], tile).isNotEmpty()

            if (canHu || canGang || canPeng || canChi) {
                responses[i] = PlayerResponses(canHu, canGang, canPeng, canChi)
            }
        }

        // 没人能回应 → 下家直接摸牌
        if (responses.isEmpty()) {
            return advanceToNextDraw(playerIndex)
        }

        return DiscardResult.waitingResponse(buildState(), responses)
    }

    /** 胡 */
    fun hu(playerIndex: Int): HuResult {
        require(phase == Phase.WAIT_RESPONSE) { "当前不在回应阶段" }
        require(playerIndex != lastDiscardPlayer) { "不能胡自己打的牌" }

        hands[playerIndex].add(lastDiscard!!)
        phase = Phase.GAME_OVER

        val winType = WinType.DIAN_PAO
        val fan = WinPattern.calculateFan(
            hands[playerIndex], melds[playerIndex], winType, playerIndex == dealerIndex
        )

        return HuResult(buildState(), playerIndex, lastDiscardPlayer!!, fan, winType)
    }

    /** 杠 */
    fun gang(playerIndex: Int, type: MeldType = MeldType.GANG_OPEN): GangResult {
        require(phase == Phase.WAIT_DISCARD || phase == Phase.WAIT_RESPONSE)

        when (type) {
            MeldType.GANG_HIDDEN -> {
                // 暗杠
                val hidden = MeldDetector.findHiddenGang(hands[playerIndex])
                    ?: return GangResult.invalid("没有可暗杠的牌")
                hands[playerIndex].removeAll(hidden)
                melds[playerIndex].add(Meld(MeldType.GANG_HIDDEN, hidden, null))
            }
            MeldType.GANG_ADDED -> {
                // 加杠
                val added = MeldDetector.findAddedGang(hands[playerIndex], melds[playerIndex])
                    ?: return GangResult.invalid("没有可加杠的牌")
                hands[playerIndex].remove(added!!)
                val targetMeld = melds[playerIndex].find {
                    it.type == MeldType.PENG && it.tiles.any { t ->
                        t.suit == added.suit && t.value == added.value
                    }
                }!!
                melds[playerIndex].remove(targetMeld)
                melds[playerIndex].add(Meld(
                    MeldType.GANG_ADDED,
                    targetMeld.tiles + added,
                    targetMeld.fromPlayer
                ))
            }
            else -> {
                // 明杠
                require(lastDiscard != null) { "没有可杠的牌" }
                val tile = lastDiscard!!
                if (!MeldDetector.canGangOpen(hands[playerIndex], tile)) {
                    return GangResult.invalid("不能杠这张牌")
                }
                val same = hands[playerIndex].filter {
                    it.suit == tile.suit && it.value == tile.value
                }.take(3)
                hands[playerIndex].removeAll(same)
                melds[playerIndex].add(Meld(MeldType.GANG_OPEN, same + tile, lastDiscardPlayer))
                lastDiscard = null
            }
        }

        // 杠完补牌
        val (tile, newWall) = TileWall.draw(wall)
        wall = newWall

        if (tile == null) {
            phase = Phase.DRAW_GAME
            return GangResult.drawGame(buildState())
        }

        hands[playerIndex].add(tile)

        // 检查杠上开花
        if (WinPattern.canWin(hands[playerIndex].toList(), melds[playerIndex].toList())) {
            phase = Phase.GAME_OVER
            val fan = WinPattern.calculateFan(
                hands[playerIndex], melds[playerIndex], WinType.GANG_KAI, playerIndex == dealerIndex
            )
            return GangResult.gangKai(buildState(), playerIndex, fan)
        }

        drawnTile = tile
        currentPlayer = playerIndex
        phase = Phase.WAIT_DISCARD

        return GangResult.success(buildState())
    }

    /** 碰 */
    fun peng(playerIndex: Int): PengResult {
        require(phase == Phase.WAIT_RESPONSE) { "当前不在回应阶段" }
        val tile = lastDiscard ?: return PengResult.invalid("没有可碰的牌")

        if (!MeldDetector.canPeng(hands[playerIndex], tile)) {
            return PengResult.invalid("不能碰这张牌")
        }

        val same = hands[playerIndex].filter {
            it.suit == tile.suit && it.value == tile.value
        }.take(2)
        hands[playerIndex].removeAll(same)
        melds[playerIndex].add(Meld(MeldType.PENG, same + tile, lastDiscardPlayer))
        lastDiscard = null

        currentPlayer = playerIndex
        phase = Phase.WAIT_DISCARD

        return PengResult.success(buildState())
    }

    /** 吃 */
    fun chi(playerIndex: Int, chiTiles: List<Tile>): ChiResult {
        require(phase == Phase.WAIT_RESPONSE) { "当前不在回应阶段" }
        require(playerIndex == (lastDiscardPlayer!! + 1) % 4) { "只能吃上家的牌" }

        val options = MeldDetector.canChi(hands[playerIndex], lastDiscard!!)
        val matched = options.find { option ->
            option.size == chiTiles.size &&
            option.all { t -> chiTiles.any { it.suit == t.suit && it.value == t.value } }
        }

        if (matched == null) return ChiResult.invalid("不合法吃法")

        // 从手牌移除（保留 discard 那张）
        val toRemove = matched.filter { it.id != lastDiscard!!.id || it.suit != lastDiscard!!.suit || it.value != lastDiscard!!.value }
        hands[playerIndex].removeAll(toRemove)
        melds[playerIndex].add(Meld(MeldType.CHI, matched, lastDiscardPlayer))
        lastDiscard = null

        currentPlayer = playerIndex
        phase = Phase.WAIT_DISCARD

        return ChiResult.success(buildState())
    }

    /** 过（不操作） */
    fun pass(): PassResult {
        require(phase == Phase.WAIT_RESPONSE) { "当前不在回应阶段" }
        return advanceToNextDraw(lastDiscardPlayer!!)
    }

    /** 获取当前状态 */
    fun getState(): MahjongState = buildState()
    fun getHand(playerIndex: Int): List<Tile> = hands[playerIndex].toList()
    fun getMelds(playerIndex: Int): List<Meld> = melds[playerIndex].toList()

    // ===== 私有 =====

    private fun advanceToNextDraw(fromPlayer: Int): DiscardResult {
        lastDiscard = null
        currentPlayer = (fromPlayer + 1) % 4
        phase = Phase.WAIT_DRAW

        if (wall.isEmpty()) {
            phase = Phase.DRAW_GAME
            return DiscardResult.drawGame(buildState())
        }

        return DiscardResult.nextDraw(buildState())
    }

    private fun buildState(): MahjongState {
        return MahjongState(
            phase = phase.name,
            currentPlayer = currentPlayer,
            dealerIndex = dealerIndex,
            handSizes = hands.map { it.size },
            melds = melds.map { it.toList() },
            lastDiscard = lastDiscard,
            lastDiscardPlayer = lastDiscardPlayer,
            wallRemaining = wall.size,
            drawnTile = drawnTile
        )
    }
}

// ================================================================
// 结果类型
// ================================================================

data class MahjongState(
    val phase: String,
    val currentPlayer: Int,
    val dealerIndex: Int,
    val handSizes: List<Int>,
    val melds: List<List<Meld>>,
    val lastDiscard: Tile?,
    val lastDiscardPlayer: Int,
    val wallRemaining: Int,
    val drawnTile: Tile?
)

data class PlayerResponses(
    val canHu: Boolean,
    val canGang: Boolean,
    val canPeng: Boolean,
    val canChi: Boolean
)

sealed class DrawResult {
    data class ReadyToDiscard(val state: MahjongState, val canHiddenGang: Boolean, val canAddedGang: Boolean) : DrawResult()
    data class Zimo(val state: MahjongState, val winner: Int, val fan: Int) : DrawResult()
    data class DrawGame(val state: MahjongState) : DrawResult()
}

sealed class DiscardResult {
    data class WaitingResponse(val state: MahjongState, val responses: Map<Int, PlayerResponses>) : DiscardResult()
    data class NextDraw(val state: MahjongState) : DiscardResult()
    data class DrawGame(val state: MahjongState) : DiscardResult()
}

sealed class HuResult {
    data class Success(val state: MahjongState, val winner: Int, val loser: Int, val fan: Int, val winType: WinType) : HuResult()
}

sealed class GangResult {
    data class Success(val state: MahjongState) : GangResult()
    data class GangKai(val state: MahjongState, val winner: Int, val fan: Int) : GangResult()
    data class DrawGame(val state: MahjongState) : GangResult()
    data class Invalid(val reason: String) : GangResult()
}

sealed class PengResult {
    data class Success(val state: MahjongState) : PengResult()
    data class Invalid(val reason: String) : PengResult()
}

sealed class ChiResult {
    data class Success(val state: MahjongState) : ChiResult()
    data class Invalid(val reason: String) : ChiResult()
}

sealed class PassResult {
    data class NextDraw(val state: MahjongState) : PassResult()
    data class DrawGame(val state: MahjongState) : PassResult()
}