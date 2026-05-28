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
import kotlinx.coroutines.delay
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
    var screen by remember { mutableStateOf(Screen.LOBBY) }
    var playerName by remember { mutableStateOf("玩家") }
    var myPoints by remember { mutableStateOf(10000) }
    val playerId = remember { UUID.randomUUID().toString().take(8) }

    var engineState by remember { mutableStateOf<com.qipaishi.game.doudizhu.engine.GameState?>(null) }
    var selectedCards by remember { mutableStateOf<Set<Card>>(emptySet()) }
    var gameOverMsg by remember { mutableStateOf<String?>(null) }
    var roomPlayers by remember { mutableStateOf<List<DoudizhuRoomHost.PlayerInfo>>(emptyList()) }
    var roomHost by remember { mutableStateOf<DoudizhuRoomHost?>(null) }

    val scope = rememberCoroutineScope()
    val scanner = remember { RoomScanner() }
    val rooms by scanner.rooms.collectAsState()

    LaunchedEffect(Unit) { scanner.start(this) }

    fun createDoudizhu() {
        val host = DoudizhuRoomHost(playerId, playerName, myPoints)
        host.start(UUID.randomUUID().toString().take(6), scope)
        roomHost = host
        roomPlayers = host.getRoomPlayers()
        screen = Screen.ROOM
        gameOverMsg = null
        // 1.5 秒后自动开游戏
        scope.launch { delay(1500); host.startGame() }
    }

    fun leaveRoom() {
        roomHost?.stop(); roomHost = null
        engineState = null; gameOverMsg = null; selectedCards = emptySet()
        screen = Screen.LOBBY
    }

    when (screen) {
        Screen.LOBBY -> LobbyScreen(
            playerName = playerName, points = myPoints, rooms = rooms,
            onCreateRoom = { createDoudizhu() },
            onJoinRoom = { _, _ -> createDoudizhu() }
        )

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
            LaunchedEffect(roomHost) {
                roomHost?.events?.collect { event ->
                    when (event) {
                        is DoudizhuRoomHost.RoomEvent.StateUpdated -> {
                            engineState = event.state
                            roomPlayers = roomHost?.getRoomPlayers() ?: roomPlayers
                            if (screen == Screen.ROOM || screen == Screen.DOUDIZHU_GAME)
                                screen = Screen.DOUDIZHU_GAME
                        }
                        is DoudizhuRoomHost.RoomEvent.GameEnded -> {
                            engineState = event.state
                            val scores = event.result.calculateScores()
                            val landlordName = roomHost?.getRoomPlayers()?.find { it.index == event.state.landlordIndex }?.name ?: "地主"
                            val myDelta = scores[0] ?: 0
                            myPoints += myDelta
                            val emoji = if (myDelta > 0) "\uD83C\uDF89" else "\uD83D\uDE22"
                            gameOverMsg = "$emoji ${if (myDelta > 0) "赢了" else "输了"} ${kotlin.math.abs(myDelta)} 分\n地主: $landlordName | 倍数: ${event.result.finalMultiplier}"
                            screen = Screen.DOUDIZHU_GAME
                        }
                        else -> {}
                    }
                }
            }
        }

        Screen.DOUDIZHU_GAME -> {
            val state = engineState
            if (state == null) { leaveRoom(); return@QipaishiApp }

            val isMyTurn = state.phase == GamePhase.PLAYING && state.currentPlayerIndex == 0
            val isBidding = state.phase == GamePhase.BIDDING && state.currentPlayerIndex == 0
            val canPass = state.lastPlay != null && state.lastPlay.playerIndex != 0

            val rp = roomHost?.getRoomPlayers() ?: roomPlayers
            val opp1 = rp.getOrElse(1) { DoudizhuRoomHost.PlayerInfo("", "对手1", 1, 0) }
            val opp2 = rp.getOrElse(2) { DoudizhuRoomHost.PlayerInfo("", "对手2", 2, 0) }

            DoudizhuGameScreen(
                opponent1Name = opp1.name, opponent2Name = opp2.name,
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
                onCardToggle = { card ->
                    if (isBidding) return@DoudizhuGameScreen
                    selectedCards = selectedCards.let { s -> if (card in s) s - card else s + card }
                },
                onPlay = {
                    val c = selectedCards.toList()
                    if (c.isNotEmpty() && roomHost?.hostPlay(c) == true) selectedCards = emptySet()
                },
                onPass = { roomHost?.hostPass() },
                onBid = { roomHost?.hostBid(it) },
                onHint = {},
                onLeave = { leaveRoom() },
                gameOverMsg = gameOverMsg,
                onContinue = { gameOverMsg = null }
            )
        }

        Screen.MAHJONG_GAME -> {}
    }
}

enum class Screen { LOBBY, ROOM, DOUDIZHU_GAME, MAHJONG_GAME }