package com.qipaishi.core

import com.qipaishi.game.mahjong.engine.*
import com.qipaishi.game.mahjong.model.*
import com.qipaishi.network.*
import com.qipaishi.network.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * 麻将房间主机（房主端）
 *
 * 协调 MahjongEngine + 网络 + 积分
 */
class MahjongRoomHost(
    private val playerId: String,
    private val playerName: String,
    private val points: Int
) {
    private val engine = MahjongEngine()
    private val server = GameServer(DoudizhuRoomHost.GAME_PORT, 4, playerId, playerName, points)
    private val scoreManager = ScoreManager(playerId)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var players = listOf<Pair<String, String>>()  // id → name
    private var playerIds = mutableMapOf<Int, String>()  // index → id
    private var isRunning = false
    private var broadcaster: RoomBroadcaster? = null

    data class MahjongPlayerInfo(
        val id: String,
        val name: String,
        val index: Int,
        val points: Int
    )

    fun start(roomId: String) {
        isRunning = true
        playerIds[0] = playerId
        players = listOf(playerId to playerName)

        server.start(scope)

        broadcaster = RoomBroadcaster(RoomInfo(
            roomId = roomId,
            hostName = playerName,
            game = "mahjong",
            playerCount = 1,
            maxPlayers = 4,
            hostAddress = DoudizhuRoomHost.getLocalIpAddress()
        ))
        broadcaster!!.start(scope)

        scope.launch {
            server.messages.collect { incoming ->
                handleMessage(incoming.playerIndex, incoming.playerId, incoming.message)
            }
        }
    }

    private fun handleMessage(playerIndex: Int, playerId: String, msg: GameMessage) {
        when (msg.type) {
            MessageType.JOIN -> {
                val data = Json.decodeFromJsonElement<JoinRequest>(msg.data!!)
                players = players + (data.playerId to data.playerName)
                playerIds[playerIndex] = data.playerId
                server.broadcast(MessageType.PLAYER_JOINED,
                    PlayerJoined(data.playerId, data.playerName, playerIndex, data.points))

                if (players.size == 4) startGame()
            }
            "MAHJONG_DISCARD" -> {
                val suit = msg.data?.jsonObject?.get("suit")?.jsonPrimitive?.content ?: return
                val value = msg.data.jsonObject["value"]?.jsonPrimitive?.int ?: return
                val id = msg.data.jsonObject["id"]?.jsonPrimitive?.int ?: return
                val hand = engine.getHand(playerIndex)
                val tile = hand.find { it.suit.name == suit && it.value == value && it.id == id } ?: return
                val result = engine.discard(playerIndex, tile)

                when (result) {
                    is DiscardResult.NextDraw -> syncAll(result.state)
                    is DiscardResult.WaitingResponse -> {
                        syncAll(result.state)
                        server.broadcast("MAHJONG_RESPONSES",
                            mapOf("responses" to result.responses.mapKeys { it.key }))
                    }
                    is DiscardResult.DrawGame -> syncAll(result.state)
                }
            }
            "MAHJONG_HU" -> {
                val result = engine.hu(playerIndex)
                if (result is HuResult.Success) {
                    syncAll(result.state)
                    handleMahjongEnd(result.winner, result.loser, result.fan, result.winType)
                }
            }
            "MAHJONG_PENG" -> {
                val result = engine.peng(playerIndex)
                if (result is PengResult.Success) syncAll(result.state)
            }
            "MAHJONG_CHI" -> { /* 简化处理 */ }
            "MAHJONG_GANG" -> engine.gang(playerIndex)
            "MAHJONG_PASS" -> {
                engine.pass()
                val state = engine.getState()
                syncAll(state)
            }
            MessageType.PLAYER_LEAVE -> handlePlayerLeave(playerIndex)
        }
    }

    private fun startGame() {
        val state = engine.newGame(0)
        // 触发庄家摸牌
        val drawResult = engine.draw(state.currentPlayer)
        when (drawResult) {
            is DrawResult.ReadyToDiscard -> syncAll(drawResult.state)
            is DrawResult.Zimo -> handleMahjongEnd(drawResult.winner, -1, drawResult.fan, WinType.ZIMO)
            else -> {}
        }
    }

    private fun handleMahjongEnd(winner: Int, loser: Int, fan: Int, winType: WinType) {
        val base = 1000 * fan

        if (winType == WinType.ZIMO || winType == WinType.GANG_KAI ||
            winType == WinType.TIAN_HU || winType == WinType.DI_HU || winType == WinType.HAIDI) {
            // 自摸：其他三人各付 base
            for (i in 0..3) {
                if (i != winner) scoreManager.addPoints(playerIds[i] ?: return, -base)
            }
            scoreManager.addPoints(playerIds[winner] ?: return, base * 3)
        } else {
            // 点炮：点炮者付 base×3
            scoreManager.addPoints(playerIds[loser] ?: return, -base * 3)
            scoreManager.addPoints(playerIds[winner] ?: return, base * 3)
        }

        server.broadcast(MessageType.GAME_OVER, GameOverInfo(
            winner = winner,
            winnerSide = if (winType == WinType.ZIMO) "ZIMO" else "DIANPAO",
            scores = (0..3).associate { i ->
                (playerIds[i] ?: "") to scoreManager.getPoints(playerIds[i] ?: "")
            },
            finalMultiplier = fan
        ))

        // 3 秒后自动下一局
        scope.launch {
            delay(3000)
            startGame()
        }
    }

    private fun syncAll(state: MahjongState) {
        players.forEachIndexed { _, (id, _) ->
            server.sendTo(id, MessageType.SYNC_STATE, state)
        }
    }

    private fun handlePlayerLeave(playerIndex: Int) {
        players = players.filterIndexed { i, _ -> playerIds[i] != playerIndex.toString() }
    }

    fun stop() {
        isRunning = false
        broadcaster?.stop()
        server.stop()
        scope.cancel()
    }
}