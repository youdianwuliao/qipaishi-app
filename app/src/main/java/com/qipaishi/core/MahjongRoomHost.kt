package com.qipaishi.core

import com.qipaishi.game.mahjong.engine.*
import com.qipaishi.game.mahjong.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 麻将房间主机
 */
class MahjongRoomHost(
    private val playerId: String,
    private val playerName: String,
    private val points: Int
) {
    val engine = MahjongEngine()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _players = mutableListOf<DoudizhuRoomHost.PlayerInfo>()
    private var isRunning = false

    private val _events = MutableSharedFlow<MahjongRoomEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<MahjongRoomEvent> = _events.asSharedFlow()

    sealed class MahjongRoomEvent {
        data class StateUpdated(val state: MahjongState, val responses: Map<Int, PlayerResponses>? = null) : MahjongRoomEvent()
        data class GameEnded(val state: MahjongState, val message: String) : MahjongRoomEvent()
    }

    fun start(roomId: String) {
        isRunning = true
        _players.clear()
        _players.add(DoudizhuRoomHost.PlayerInfo(playerId, playerName, 0, points))
        _players.add(DoudizhuRoomHost.PlayerInfo("ai_1", "AI雀士A", 1, 10000))
        _players.add(DoudizhuRoomHost.PlayerInfo("ai_2", "AI雀士B", 2, 10000))
        _players.add(DoudizhuRoomHost.PlayerInfo("ai_3", "AI雀士C", 3, 10000))
    }

    fun startGame() {
        val state = engine.newGame()
        _events.tryEmit(MahjongRoomEvent.StateUpdated(state))
        autoAdvanceIfNeeded(state)
    }

    fun hostDraw() {
        val state = engine.getState()
        if (state.phase != "WAIT_DRAW" || state.currentPlayer != 0) return
        val result = engine.draw(0)
        when (result) {
            is DrawResult.ReadyToDiscard -> _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state))
            is DrawResult.Zimo -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "🎉 自摸！${result.fan} 番"))
            is DrawResult.DrawGame -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "流局"))
        }
    }

    fun hostDiscard(tile: Tile) {
        val result = engine.discard(0, tile)
        handleDiscardResult(result)
    }

    fun hostResponse(action: String, chiTiles: List<Tile> = emptyList()) {
        val result = when (action) {
            "hu" -> { val r = engine.hu(0); _events.tryEmit(MahjongRoomEvent.GameEnded(
                (r as? HuResult.Success)?.state ?: engine.getState(), "🎉 胡了！"))
                return }
            "gang" -> handleGangResult(engine.gang(0))
            "peng" -> handlePengResult(engine.peng(0))
            "chi" -> handleChiResult(engine.chi(0, chiTiles))
            "pass" -> handlePassResult(engine.pass())
            else -> null
        }
    }

    // ===== AI 自动推进 =====

    private fun autoAdvanceIfNeeded(state: MahjongState) {
        if (state.currentPlayer == 0) return // 等人操作
        aiPlay(state.currentPlayer)
    }

    private fun aiPlay(playerIdx: Int) {
        scope.launch {
            delay(500)
            val state = engine.getState()
            if (state.currentPlayer != playerIdx) return@launch

            when (state.phase) {
                "WAIT_DRAW" -> {
                    val result = engine.draw(playerIdx)
                    when (result) {
                        is DrawResult.ReadyToDiscard -> {
                            _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state))
                            delay(300)
                            val hand = engine.getHand(playerIdx)
                            val tile = MahjongAI.decideDiscard(hand, state.drawnTile)
                            val dResult = engine.discard(playerIdx, tile)
                            handleDiscardResult(dResult)
                        }
                        is DrawResult.Zimo -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "AI 自摸！${result.fan} 番"))
                        is DrawResult.DrawGame -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "流局"))
                    }
                }
                "WAIT_RESPONSE" -> {
                    // AI 回应：找到一个能回应的 AI，让它回应
                    val respState = state
                    // Find a non-host player who can respond
                    val responders = (1..3).toList()
                    var handled = false
                    for (idx in responders) {
                        val hand = engine.getHand(idx)
                        val lastTile = state.lastDiscard ?: continue
                        val canHu = WinPattern.canWin(hand + lastTile, engine.getMelds(idx))
                        val canGang = MeldDetector.canGangOpen(hand, lastTile)
                        val canPeng = MeldDetector.canPeng(hand, lastTile)
                        val canChi = idx == (state.lastDiscardPlayer + 1) % 4 && MeldDetector.canChi(hand, lastTile).isNotEmpty()

                        val resp = PlayerResponses(canHu, canGang, canPeng, canChi)
                        if (canHu || canGang || canPeng || canChi) {
                            val action = MahjongAI.decideResponse(resp)
                            when (action) {
                                "hu" -> {
                                    val r = engine.hu(idx)
                                    if (r is HuResult.Success) _events.tryEmit(MahjongRoomEvent.GameEnded(r.state, "AI 胡了！${r.fan} 番"))
                                    handled = true; break
                                }
                                "gang" -> {
                                    val r = handleGangResult(engine.gang(idx))
                                    handled = true; break
                                }
                                "peng" -> {
                                    val r = handlePengResult(engine.peng(idx))
                                    handled = true; break
                                }
                                "chi" -> {
                                    val options = MeldDetector.canChi(hand, lastTile)
                                    val chosen = MahjongAI.decideChi(options)
                                    val r = handleChiResult(engine.chi(idx, chosen))
                                    handled = true; break
                                }
                            }
                        }
                    }
                    if (!handled) {
                        val pResult = engine.pass()
                        handlePassResult(pResult)
                    }
                }
            }
        }
    }

    // ===== 结果处理 =====

    private fun handleDiscardResult(result: DiscardResult) {
        when (result) {
            is DiscardResult.WaitingResponse -> {
                _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state, result.responses))
                // 如果有需要回应的 AI 玩家，自动回应
                aiPlay(result.state.currentPlayer) // 这会触发 AI 的 response 处理
            }
            is DiscardResult.NextDraw -> {
                _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state))
                autoAdvanceIfNeeded(result.state)
            }
            is DiscardResult.DrawGame -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "流局"))
        }
    }

    private fun handleGangResult(result: GangResult) {
        when (result) {
            is GangResult.Success -> {
                _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state))
                autoAdvanceIfNeeded(result.state)
            }
            is GangResult.GangKai -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "🎉 杠上开花！${result.winner} 番"))
            is GangResult.DrawGame -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "流局"))
            is GangResult.Invalid -> {}
        }
    }

    private fun handlePengResult(result: PengResult) {
        when (result) {
            is PengResult.Success -> {
                _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state))
                autoAdvanceIfNeeded(result.state)
            }
            is PengResult.Invalid -> {}
        }
    }

    private fun handleChiResult(result: ChiResult) {
        when (result) {
            is ChiResult.Success -> {
                _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state))
                autoAdvanceIfNeeded(result.state)
            }
            is ChiResult.Invalid -> {}
        }
    }

    private fun handlePassResult(result: PassResult) {
        when (result) {
            is PassResult.NextDraw -> {
                _events.tryEmit(MahjongRoomEvent.StateUpdated(result.state))
                autoAdvanceIfNeeded(result.state)
            }
            is PassResult.DrawGame -> _events.tryEmit(MahjongRoomEvent.GameEnded(result.state, "流局"))
        }
    }

    fun getPlayers(): List<DoudizhuRoomHost.PlayerInfo> = _players.toList()

    fun stop() { isRunning = false; scope.cancel() }
}