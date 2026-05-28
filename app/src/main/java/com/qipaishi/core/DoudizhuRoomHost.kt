package com.qipaishi.core

import com.qipaishi.game.doudizhu.engine.*
import com.qipaishi.game.doudizhu.model.*
import com.qipaishi.network.*
import com.qipaishi.network.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 斗地主房间主机（房主端）
 */
class DoudizhuRoomHost(
    private val playerId: String,
    private val playerName: String,
    private val points: Int
) {
    /** 对外暴露引擎，UI 直接操作 */
    val engine = DoudizhuEngine()

    private val server = GameServer(GAME_PORT, 3, playerId, playerName, points)
    private val scoreManager = ScoreManager(playerId)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _players = mutableListOf<PlayerInfo>()
    private val _playerIds = mutableMapOf<Int, String>()
    private var broadcaster: RoomBroadcaster? = null
    private var isRunning = false

    /** 对外事件流 */
    private val _events = MutableSharedFlow<RoomEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<RoomEvent> = _events.asSharedFlow()

    data class PlayerInfo(val id: String, val name: String, val index: Int, val points: Int)

    /** 房间事件 */
    sealed class RoomEvent {
        data class PlayerJoined(val players: List<PlayerInfo>) : RoomEvent()
        data class PlayerLeft(val players: List<PlayerInfo>) : RoomEvent()
        data class StateUpdated(val state: GameState) : RoomEvent()
        data class GameEnded(val state: GameState, val result: GameResult) : RoomEvent()
    }

    fun start(roomId: String, externalScope: CoroutineScope? = null) {
        isRunning = true
        val s = externalScope ?: scope

        scoreManager.getOrCreate(playerId, playerName)
        _playerIds[0] = playerId
        _players.add(PlayerInfo(playerId, playerName, 0, points))
        _events.tryEmit(RoomEvent.PlayerJoined(_players.toList()))

        server.start(s)

        broadcaster = RoomBroadcaster(RoomInfo(
            roomId = roomId, hostName = playerName,
            game = "doudizhu", playerCount = 1, maxPlayers = 3,
            hostAddress = getLocalIpAddress()
        ))
        broadcaster!!.start(s)

        s.launch {
            server.messages.collect { incoming ->
                handleMessage(incoming.playerIndex, incoming.playerId, incoming.message)
            }
        }
    }

    fun getRoomPlayers(): List<PlayerInfo> = _players.toList()

    fun getMyPoints(myId: String): Int = scoreManager.getPoints(myId)

    // ===== 事件驱动的主机操作 =====

    fun startGame() {
        val state = engine.newGame()
        _events.tryEmit(RoomEvent.StateUpdated(state))
    }

    fun hostBid(score: Int) {
        val result = engine.bid(0, score)
        when (result) {
            is BidActionResult.ContinueBidding -> _events.tryEmit(RoomEvent.StateUpdated(result.state))
            is BidActionResult.BiddingDone -> _events.tryEmit(RoomEvent.StateUpdated(result.state))
            is BidActionResult.Restart -> {
                _events.tryEmit(RoomEvent.StateUpdated(result.state))
                scope.launch { delay(500); startGame() }
            }
        }
    }

    fun hostPlay(cards: List<Card>): Boolean {
        val result = engine.play(0, cards)
        return when (result) {
            is PlayActionResult.Accepted -> { _events.tryEmit(RoomEvent.StateUpdated(result.state)); true }
            is PlayActionResult.Passed -> { _events.tryEmit(RoomEvent.StateUpdated(result.state)); true }
            is PlayActionResult.GameOver -> {
                _events.tryEmit(RoomEvent.GameEnded(result.state, result.result)); true
            }
            is PlayActionResult.Invalid -> false
        }
    }

    fun hostPass(): Boolean {
        val result = engine.pass(0)
        return when (result) {
            is PlayActionResult.Passed -> { _events.tryEmit(RoomEvent.StateUpdated(result.state)); true }
            is PlayActionResult.GameOver -> {
                _events.tryEmit(RoomEvent.GameEnded(result.state, result.result)); true
            }
            else -> false
        }
    }

    // ===== 网络消息处理 =====

    private fun handleMessage(playerIndex: Int, pid: String, msg: GameMessage) {
        when (msg.type) {
            MessageType.JOIN -> {
                val data = parseJoin(msg.data) ?: return
                _players.add(PlayerInfo(data.playerId, data.playerName, playerIndex, data.points))
                _playerIds[playerIndex] = data.playerId
                server.broadcast(MessageType.PLAYER_JOINED, PlayerJoined(
                    data.playerId, data.playerName, playerIndex, data.points))
                _events.tryEmit(RoomEvent.PlayerJoined(_players.toList()))
                if (_players.size == 3) startGame()
            }
            MessageType.BID -> {
                val bidData = parseBid(msg.data) ?: return
                val result = engine.bid(playerIndex, bidData.score)
                when (result) {
                    is BidActionResult.ContinueBidding -> syncToAll(result.state)
                    is BidActionResult.BiddingDone -> syncToAll(result.state)
                    is BidActionResult.Restart -> { syncToAll(result.state); scope.launch { delay(500); startGame() } }
                }
            }
            MessageType.PLAY -> {
                val playData = parsePlay(msg.data) ?: return
                val hand = engine.getHand(playerIndex)
                val cards = playData.cardRanks.zip(playData.cardSuits).mapNotNull { (r, s) ->
                    hand.find { it.rank == r && it.suit.symbol == s }
                }
                val result = engine.play(playerIndex, cards)
                when (result) {
                    is PlayActionResult.Accepted -> syncToAll(result.state)
                    is PlayActionResult.Passed -> syncToAll(result.state)
                    is PlayActionResult.GameOver -> { syncToAll(result.state); onGameOver(result.state, result.result) }
                    is PlayActionResult.Invalid -> server.sendTo(pid, MessageType.PLAY_RESULT, PlayResult(false, "invalid"))
                }
            }
            MessageType.PASS -> {
                val result = engine.pass(playerIndex)
                when (result) {
                    is PlayActionResult.Passed -> syncToAll(result.state)
                    is PlayActionResult.GameOver -> { syncToAll(result.state); onGameOver(result.state, result.result) }
                    else -> {}
                }
            }
            MessageType.PLAYER_LEAVE -> {
                val left = _players.find { it.index == playerIndex }
                if (left != null) { _players.remove(left); _playerIds.remove(playerIndex) }
                _events.tryEmit(RoomEvent.PlayerLeft(_players.toList()))
            }
        }
    }

    private fun syncToAll(state: GameState) {
        _events.tryEmit(RoomEvent.StateUpdated(state))
        _players.forEach { player ->
            val data = SyncState(
                phase = state.phase.name, currentPlayerIndex = state.currentPlayerIndex,
                landlordIndex = state.landlordIndex, handSizes = state.handSizes,
                myCards = if (player.index == 0) engine.getHand(0).map { "${it.suit.symbol}${it.rank}" }
                          else emptyList(),
                bottomCards = state.bottomCards.map { "${it.suit.symbol}${it.rank}" },
                lastPlayedCards = state.lastPlay?.group?.cards?.map { "${it.suit.symbol}${it.rank}" },
                lastPlayedType = state.lastPlay?.group?.type?.description,
                lastPlayedBy = state.lastPlay?.playerIndex,
                bidMultiplier = state.bidMultiplier, bombCount = state.bombCount,
                winner = state.winner, scores = null)
            if (player.index != 0) server.sendTo(player.id, MessageType.SYNC_STATE, data)
        }
    }

    private fun onGameOver(state: GameState, result: GameResult) {
        scoreManager.settleGame(_playerIds, result.calculateScores())
        server.broadcast(MessageType.GAME_OVER, GameOverInfo(
            winner = state.winner ?: -1, winnerSide = result.winnerSide.name,
            scores = result.calculateScores(), finalMultiplier = result.finalMultiplier))
        _events.tryEmit(RoomEvent.GameEnded(state, result))
        scope.launch { delay(3000); if (isRunning) startGame() }
    }

    private fun parseJoin(d: kotlinx.serialization.json.JsonElement?) =
        try { kotlinx.serialization.json.Json.decodeFromJsonElement(JoinRequest.serializer(), d!!) } catch (e: Exception) { null }
    private fun parseBid(d: kotlinx.serialization.json.JsonElement?) =
        try { kotlinx.serialization.json.Json.decodeFromJsonElement(BidAction.serializer(), d!!) } catch (e: Exception) { null }
    private fun parsePlay(d: kotlinx.serialization.json.JsonElement?) =
        try { kotlinx.serialization.json.Json.decodeFromJsonElement(PlayAction.serializer(), d!!) } catch (e: Exception) { null }

    fun stop() { isRunning = false; broadcaster?.stop(); server.stop(); scope.cancel() }

    companion object {
        const val GAME_PORT = 9528
        fun getLocalIpAddress(): String {
            return java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it.hostAddress.indexOf(':') == -1 }
                .map { it.hostAddress }.firstOrNull() ?: "127.0.0.1"
        }
    }
}