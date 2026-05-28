package com.qipaishi.core

import com.qipaishi.game.doudizhu.engine.*
import com.qipaishi.game.doudizhu.model.*
import com.qipaishi.network.*
import com.qipaishi.network.protocol.*
import kotlinx.coroutines.*

/**
 * 斗地主房间主机（房主端）
 *
 * 协调引擎 + 网络 + 积分，是整个游戏的后端核心。
 *
 * 流程：
 * 1. 创建房间 → 启动 GameServer + RoomBroadcaster
 * 2. 玩家加入 → 更新列表 → 满 3 人自动开始
 * 3. 调用 Engine 处理每一步 → 广播状态
 * 4. 游戏结束 → 结算积分 → 询问是否继续
 */
class DoudizhuRoomHost(
    private val playerId: String,
    private val playerName: String,
    private val points: Int
) {
    private val engine = DoudizhuEngine()
    private val server = GameServer(GAME_PORT, 3, playerId, playerName, points)
    private val scoreManager = ScoreManager(playerId)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var players = listOf<PlayerInfo>()
    private var nextPlayerIndex = 1  // 0 是房主
    private var playerIds = mutableMapOf<Int, String>()  // index → id
    private var readyStates = mutableMapOf<String, Boolean>()
    private var broadcaster: RoomBroadcaster? = null
    private var isRunning = false

    data class PlayerInfo(
        val id: String,
        val name: String,
        val index: Int,
        val points: Int
    )

    /**
     * 启动房间
     */
    fun start(roomId: String) {
        isRunning = true

        // 房主自己
        scoreManager.getOrCreate(playerId, playerName)
        playerIds[0] = playerId
        players = listOf(PlayerInfo(playerId, playerName, 0, points))

        // 启动 TCP Server
        server.start(scope)

        // 启动广播
        broadcaster = RoomBroadcaster(
            RoomInfo(
                roomId = roomId,
                hostName = playerName,
                game = "doudizhu",
                playerCount = 1,
                maxPlayers = 3,
                hostAddress = getLocalIpAddress()
            )
        )
        broadcaster!!.start(scope)

        // 处理消息
        scope.launch {
            server.messages.collect { incoming ->
                handleMessage(incoming.playerIndex, incoming.playerId, incoming.message)
            }
        }
    }

    /**
     * 处理客户端消息
     */
    private fun handleMessage(playerIndex: Int, playerId: String, msg: GameMessage) {
        when (msg.type) {
            MessageType.JOIN -> {
                val joinData = parseJoin(msg.data)
                if (joinData == null || !isRunning) return

                val joined = PlayerInfo(joinData.playerId, joinData.playerName, playerIndex, joinData.points)
                players = players + joined
                playerIds[playerIndex] = joinData.playerId

                // 广播新玩家加入
                server.broadcast(MessageType.PLAYER_JOINED, PlayerJoined(
                    joinData.playerId, joinData.playerName, playerIndex, joinData.points
                ))

                // 满 3 人自动开始
                if (players.size == 3) {
                    startGame()
                }
            }

            MessageType.BID -> {
                val bidData = parseBid(msg.data) ?: return
                val result = engine.bid(playerIndex, bidData.score)

                when (result) {
                    is BidActionResult.ContinueBidding -> syncStateToAll(result.state)
                    is BidActionResult.BiddingDone -> syncStateToAll(result.state)
                    is BidActionResult.Restart -> {
                        syncStateToAll(result.state)
                        // 重新发牌
                        scope.launch {
                            delay(1000)
                            val newState = engine.newGame()
                            syncStateToAll(newState)
                        }
                    }
                }
            }

            MessageType.PLAY -> {
                val playData = parsePlay(msg.data) ?: return
                val hand = engine.getHand(playerIndex)
                val cards = playData.cardRanks.zip(playData.cardSuits).mapNotNull { (rank, suitStr) ->
                    hand.find { it.rank == rank && it.suit.symbol == suitStr }
                }

                val result = engine.play(playerIndex, cards)

                when (result) {
                    is PlayActionResult.Accepted -> {
                        syncStateToAll(result.state)
                        // 检查炸弹
                        val isBomb = lastPlayType(result.state) in listOf("炸弹", "火箭")
                        val playResult = PlayResult(accepted = true, isBomb = isBomb)
                        broadcastExcept(playerIndex, MessageType.PLAY_RESULT, playResult)
                    }
                    is PlayActionResult.Passed -> syncStateToAll(result.state)
                    is PlayActionResult.GameOver -> {
                        syncStateToAll(result.state)
                        handleGameOver(result.state, result.result)
                    }
                    is PlayActionResult.Invalid -> {
                        server.sendTo(playerId, MessageType.PLAY_RESULT,
                            PlayResult(accepted = false, reason = result.reason))
                    }
                }
            }

            MessageType.PASS -> {
                val result = engine.pass(playerIndex)
                when (result) {
                    is PlayActionResult.Passed -> syncStateToAll(result.state)
                    is PlayActionResult.GameOver -> {
                        syncStateToAll(result.state)
                        handleGameOver(result.state, result.result)
                    }
                    else -> {}
                }
            }

            MessageType.PLAYER_LEAVE -> handlePlayerLeave(playerIndex)
        }
    }

    private fun startGame() {
        val state = engine.newGame()
        syncStateToAll(state)
    }

    private fun handleGameOver(engineState: GameState, result: GameResult) {
        // 结算积分
        val scoreDeltas = result.calculateScores()
        scoreManager.settleGame(playerIds, scoreDeltas)

        // 广播结算
        server.broadcast(MessageType.GAME_OVER, GameOverInfo(
            winner = engineState.winner ?: -1,
            winnerSide = result.winnerSide.name,
            scores = scoreDeltas,
            finalMultiplier = result.finalMultiplier
        ))

        // 3 秒后自动开始下一局
        scope.launch {
            delay(3000)
            if (isRunning && players.size == 3) {
                startGame()
            }
        }
    }

    private fun handlePlayerLeave(playerIndex: Int) {
        val leaver = players.find { it.index == playerIndex } ?: return
        players = players.filter { it.index != playerIndex }
        playerIds.remove(playerIndex)
        server.broadcast(MessageType.PLAYER_LEAVE,
            mapOf("playerId" to leaver.id, "playerName" to leaver.name))
    }

    /**
     * 同步状态给所有玩家
     */
    private fun syncStateToAll(state: GameState) {
        players.forEach { player ->
            val cardSerializer = { c: Card -> "${c.suit.symbol}${c.rank}" }

            val syncData = SyncState(
                phase = state.phase.name,
                currentPlayerIndex = state.currentPlayerIndex,
                landlordIndex = state.landlordIndex,
                handSizes = state.handSizes,
                myCards = if (player.index == 0) {
                    // 房主自己能看到全部手牌
                    engine.getHand(0).map { "${it.suit.symbol}${it.rank}" }
                } else {
                    emptyList()  // 客户端自己维护手牌
                },
                bottomCards = state.bottomCards.map { "${it.suit.symbol}${it.rank}" },
                lastPlayedCards = state.lastPlay?.group?.cards?.map(cardSerializer),
                lastPlayedType = state.lastPlay?.group?.type?.description,
                lastPlayedBy = state.lastPlay?.playerIndex,
                bidMultiplier = state.bidMultiplier,
                bombCount = state.bombCount,
                winner = state.winner,
                scores = if (state.winner != null) {
                    scoreManager.getAllScores().mapIndexed { i, ps -> i to ps.points }.toMap()
                } else null
            )

            if (player.index == 0) {
                // 房主给自己发完整手牌
                val fullSync = SyncState(
                    phase = syncData.phase,
                    currentPlayerIndex = syncData.currentPlayerIndex,
                    landlordIndex = syncData.landlordIndex,
                    handSizes = syncData.handSizes,
                    myCards = syncData.myCards,
                    bottomCards = syncData.bottomCards,
                    lastPlayedCards = syncData.lastPlayedCards,
                    lastPlayedType = syncData.lastPlayedType,
                    lastPlayedBy = syncData.lastPlayedBy,
                    bidMultiplier = syncData.bidMultiplier,
                    bombCount = syncData.bombCount,
                    winner = syncData.winner,
                    scores = syncData.scores
                )
                // 房主通过 Engine 直接访问
            } else {
                server.sendTo(player.id, MessageType.SYNC_STATE, syncData)
            }
        }
    }

    private fun broadcastExcept(playerIndex: Int, type: String, data: Any?) {
        players.filter { it.index != playerIndex }.forEach {
            server.sendTo(it.id, type, data)
        }
    }

    private fun parseJoin(data: kotlinx.serialization.json.JsonElement?): JoinRequest? {
        return try {
            kotlinx.serialization.json.Json.decodeFromJsonElement(JoinRequest.serializer(), data!!)
        } catch (e: Exception) { null }
    }

    private fun parseBid(data: kotlinx.serialization.json.JsonElement?): BidAction? {
        return try {
            kotlinx.serialization.json.Json.decodeFromJsonElement(BidAction.serializer(), data!!)
        } catch (e: Exception) { null }
    }

    private fun parsePlay(data: kotlinx.serialization.json.JsonElement?): PlayAction? {
        return try {
            kotlinx.serialization.json.Json.decodeFromJsonElement(PlayAction.serializer(), data!!)
        } catch (e: Exception) { null }
    }

    private fun lastPlayType(state: GameState): String? =
        state.lastPlay?.group?.type?.description

    fun stop() {
        isRunning = false
        broadcaster?.stop()
        server.stop()
        scope.cancel()
    }

    companion object {
        const val GAME_PORT = 9528

        fun getLocalIpAddress(): String {
            return java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it.hostAddress.indexOf(':') == -1 }
                .map { it.hostAddress }
                .firstOrNull() ?: "127.0.0.1"
        }
    }
}