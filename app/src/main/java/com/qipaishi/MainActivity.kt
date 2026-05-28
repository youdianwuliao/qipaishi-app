package com.qipaishi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.qipaishi.core.DoudizhuRoomHost
import com.qipaishi.game.doudizhu.engine.GamePhase
import com.qipaishi.game.doudizhu.model.Card
import com.qipaishi.network.RoomScanner
import com.qipaishi.ui.screens.*
import com.qipaishi.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppContent() }
    }
}

@Composable
fun AppContent() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green600, secondary = Gold,
            surface = TableGreenDark, background = TableGreen,
            onPrimary = CardWhite, onSurface = CardWhite, error = ErrorRed
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            QipaishiApp()
        }
    }
}

@Composable
fun QipaishiApp() {
    // ===== 全局状态 =====
    var screen by remember { mutableStateOf(Screen.LOBBY) }
    var playerName by remember { mutableStateOf("玩家") }
    var myPoints by remember { mutableStateOf(10000) }
    var playerId by remember { mutableStateOf(UUID.randomUUID().toString().take(8)) }

    // 斗地主游戏状态
    var engineState by remember { mutableStateOf<com.qipaishi.game.doudizhu.engine.GameState?>(null) }
    var selectedCards by remember { mutableStateOf<Set<Card>>(emptySet()) }
    var gameOverMsg by remember { mutableStateOf<String?>(null) }
    var roomPlayers by remember { mutableStateOf<List<DoudizhuRoomHost.PlayerInfo>>(emptyList()) }

    // 房间主机
    var roomHost by remember { mutableStateOf<DoudizhuRoomHost?>(null) }

    val scope = rememberCoroutineScope()
    val scanner = remember { RoomScanner() }
    val rooms by scanner.rooms.collectAsState()

    // 启动扫描
    LaunchedEffect(Unit) { scanner.start(this) }

    // ===== 创建房间 =====
    fun createDoudizhu() {
        val host = DoudizhuRoomHost(playerId, playerName, myPoints)
        host.start(UUID.randomUUID().toString().take(6), scope)
        roomHost = host
        roomPlayers = host.getRoomPlayers()
        screen = Screen.ROOM

        // 没有其他玩家加入？3 秒后自己开始
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (screen == Screen.ROOM && roomPlayers.size < 3) {
                host.startGame()
            }
        }
    }

    fun leaveRoom() {
        roomHost?.stop(); roomHost = null
        engineState = null; gameOverMsg = null
        screen = Screen.LOBBY
    }

    // ===== 界面路由 =====
    when (screen) {
        Screen.LOBBY -> {
            LobbyScreen(
                playerName = playerName, points = myPoints, rooms = rooms,
                onCreateRoom = { createDoudizhu() },
                onJoinRoom = { addr, id ->
                    createDoudizhu() // 简化：点击加入启动本地游戏
                }
            )
        }

        Screen.ROOM -> {
            RoomScreen(
                roomName = "${playerName}的牌桌",
                players = (0..2).map { i ->
                    if (i < roomPlayers.size)
                        RoomPlayerInfo(roomPlayers[i].name, i, roomPlayers[i].points, true)
                    else RoomPlayerInfo("等待中...", i, 0, false)
                },
                onLeave = { leaveRoom() }
            )
            // 游戏开始事件监听
            LaunchedEffect(roomHost) {
                roomHost?.events?.collect { event ->
                    when (event) {
                        is DoudizhuRoomHost.RoomEvent.StateUpdated -> {
                            engineState = event.state
                            screen = Screen.DOUDIZHU_GAME
                        }
                        is DoudizhuRoomHost.RoomEvent.GameEnded -> {
                            engineState = event.state
                            gameOverMsg = "结算完成"
                            screen = Screen.DOUDIZHU_GAME
                        }
                        else -> {}
                    }
                }
            }
        }

        Screen.DOUDIZHU_GAME -> {
            val state = engineState
            if (state == null) {
                leaveRoom(); return
            }
            val isMyTurn = state.phase == GamePhase.PLAYING && state.currentPlayerIndex == 0
            val isBidding = state.phase == GamePhase.BIDDING && state.currentPlayerIndex == 0
            val canPass = state.lastPlay != null

            DoudizhuGameScreen(
                opponent1Name = if (roomPlayers.size >= 2) roomPlayers[1].name else "AI",
                opponent2Name = if (roomPlayers.size >= 3) roomPlayers[2].name else "AI",
                opponent1HandSize = state.handSizes.getOrElse(1) { 0 },
                opponent2HandSize = state.handSizes.getOrElse(2) { 0 },
                myPoints = myPoints,
                myCards = roomHost?.engine?.getHand(0) ?: emptyList(),
                myIsLandlord = state.landlordIndex == 0,
                opponent1IsLandlord = state.landlordIndex == 1,
                opponent2IsLandlord = state.landlordIndex == 2,
                isMyTurn = isMyTurn, canPass = canPass,
                bottomCards = state.bottomCards.map { "${it.suit.symbol}${it.rank}" },
                isBiddingPhase = isBidding,
                currentBidder = state.currentPlayerIndex,
                bidMultiplier = state.bidMultiplier, bombCount = state.bombCount,
                currentTurnPlayer = if (state.phase == GamePhase.PLAYING) state.currentPlayerIndex else -1,
                landlordIndex = state.landlordIndex,
                selectedCards = selectedCards,
                lastPlay = state.lastPlay,
                onCardToggle = { card -> selectedCards = selectedCards.let { s -> if (card in s) s - card else s + card } },
                onPlay = { val c = selectedCards.toList(); if (c.isNotEmpty() && roomHost?.hostPlay(c) == true) selectedCards = emptySet() },
                onPass = { roomHost?.hostPass() },
                onBid = { roomHost?.hostBid(it) },
                onHint = {},
                onLeave = { leaveRoom() },
                gameOverMsg = gameOverMsg,
                onContinue = { gameOverMsg = null; screen = Screen.ROOM }
            )
        }

        Screen.MAHJONG_GAME -> {}
    }
}

enum class Screen { LOBBY, ROOM, DOUDIZHU_GAME, MAHJONG_GAME }