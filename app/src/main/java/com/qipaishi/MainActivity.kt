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
import com.qipaishi.core.*
import com.qipaishi.game.doudizhu.engine.GamePhase
import com.qipaishi.game.doudizhu.model.Card
import com.qipaishi.game.mahjong.model.Tile
import com.qipaishi.network.RoomScanner
import com.qipaishi.ui.screens.*
import com.qipaishi.ui.theme.*
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

    // 斗地主
    var engineState by remember { mutableStateOf<com.qipaishi.game.doudizhu.engine.GameState?>(null) }
    var selectedCards by remember { mutableStateOf<Set<Card>>(emptySet()) }
    var gameOverMsg by remember { mutableStateOf<String?>(null) }
    var roomPlayers by remember { mutableStateOf<List<DoudizhuRoomHost.PlayerInfo>>(emptyList()) }
    var roomHost by remember { mutableStateOf<DoudizhuRoomHost?>(null) }

    // 麻将
    var mahjongHost by remember { mutableStateOf<MahjongRoomHost?>(null) }
    var mahjongState by remember { mutableStateOf<com.qipaishi.game.mahjong.engine.MahjongState?>(null) }
    var mahjongResponses by remember { mutableStateOf<Map<Int, com.qipaishi.game.mahjong.engine.PlayerResponses>?>(null) }
    var selectedTile by remember { mutableStateOf<Tile?>(null) }

    val scope = rememberCoroutineScope()
    val scanner = remember { RoomScanner() }
    val rooms by scanner.rooms.collectAsState()

    LaunchedEffect(Unit) { scanner.start(this) }

    fun leaveRoom() {
        roomHost?.stop(); roomHost = null
        mahjongHost?.stop(); mahjongHost = null
        engineState = null; mahjongState = null
        gameOverMsg = null; selectedCards = emptySet(); selectedTile = null
        screen = Screen.LOBBY
    }

    when (screen) {
        Screen.LOBBY -> LobbyScreen(
            playerName = playerName, points = myPoints, rooms = rooms,
            onCreateRoom = {
                val host = DoudizhuRoomHost(playerId, playerName, myPoints)
                host.start(UUID.randomUUID().toString().take(6), scope)
                roomHost = host; roomPlayers = host.getRoomPlayers()
                screen = Screen.DOUDIZHU_ROOM
            },
            onCreateMahjongRoom = {
                val host = MahjongRoomHost(playerId, playerName, myPoints)
                host.start(UUID.randomUUID().toString().take(6))
                mahjongHost = host; roomPlayers = host.getPlayers()
                screen = Screen.MAHJONG_ROOM
            },
            onJoinRoom = { _, _ -> /* TODO: LAN join */ }
        )

        // ============ 斗地主 ============

        Screen.DOUDIZHU_ROOM -> {
            val host = roomHost
            if (host == null) { leaveRoom(); return@QipaishiApp }
            RoomScreen(
                roomName = "${playerName}的牌桌",
                players = (0..2).map { i ->
                    val rp = roomPlayers
                    if (i < rp.size) RoomPlayerInfo(rp[i].name, i, rp[i].points, true)
                    else RoomPlayerInfo("等待加入...", i, 0, false)
                },
                onStart = { host.startGame() },
                onLeave = { leaveRoom() }
            )
            LaunchedEffect(host) {
                host.events.collect { event ->
                    when (event) {
                        is DoudizhuRoomHost.RoomEvent.StateUpdated -> {
                            engineState = event.state
                            roomPlayers = host.getRoomPlayers()
                            screen = Screen.DOUDIZHU_GAME
                        }
                        is DoudizhuRoomHost.RoomEvent.GameEnded -> {
                            engineState = event.state
                            val delta = event.result.calculateScores()[0] ?: 0
                            myPoints += delta
                            gameOverMsg = if (delta > 0) "🎉 赢了 $delta 分" else "😅 输了 ${-delta} 分"
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

            DoudizhuGameScreen(
                opponent1Name = roomPlayers.getOrElse(1) { DoudizhuRoomHost.PlayerInfo("", "对手1", 1, 0) }.name,
                opponent2Name = roomPlayers.getOrElse(2) { DoudizhuRoomHost.PlayerInfo("", "对手2", 2, 0) }.name,
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
                onCardToggle = { if (!isBidding) selectedCards = selectedCards.let { s -> if (it in s) s - it else s + it } },
                onPlay = { val c = selectedCards.toList(); if (c.isNotEmpty() && roomHost?.hostPlay(c) == true) selectedCards = emptySet() },
                onPass = { roomHost?.hostPass() },
                onBid = { roomHost?.hostBid(it) },
                onHint = {}, onLeave = { leaveRoom() },
                gameOverMsg = gameOverMsg,
                onContinue = { gameOverMsg = null }
            )
        }

        // ============ 麻将 ============

        Screen.MAHJONG_ROOM -> {
            val host = mahjongHost
            if (host == null) { leaveRoom(); return@QipaishiApp }
            RoomScreen(
                roomName = "${playerName}的麻将房",
                players = (0..3).map { i ->
                    val rp = roomPlayers
                    if (i < rp.size) RoomPlayerInfo(rp[i].name, i, rp[i].points, true)
                    else RoomPlayerInfo("等待加入...", i, 0, false)
                },
                onStart = { mahjongHost?.startGame() },
                onLeave = { leaveRoom() }
            )
            LaunchedEffect(host) {
                host.events.collect { event ->
                    when (event) {
                        is MahjongRoomHost.MahjongRoomEvent.StateUpdated -> {
                            mahjongState = event.state
                            mahjongResponses = event.responses
                            screen = Screen.MAHJONG_GAME
                        }
                        is MahjongRoomHost.MahjongRoomEvent.GameEnded -> {
                            mahjongState = event.state
                            gameOverMsg = event.message
                            screen = Screen.MAHJONG_GAME
                        }
                    }
                }
            }
        }

        Screen.MAHJONG_GAME -> {
            val state = mahjongState
            if (state == null) { leaveRoom(); return@QipaishiApp }
            val isMyDraw = state.phase == "WAIT_DRAW" && state.currentPlayer == 0
            val isMyDiscard = state.phase == "WAIT_DISCARD" && state.currentPlayer == 0
            val isMyResponse = state.phase == "WAIT_RESPONSE"
            val canRespond = mahjongResponses?.get(0) != null

            MahjongGameScreen(
                opponent1Name = roomPlayers.getOrElse(3) { DoudizhuRoomHost.PlayerInfo("", "雀士C", 3, 0) }.name,
                opponent2Name = roomPlayers.getOrElse(2) { DoudizhuRoomHost.PlayerInfo("", "雀士B", 2, 0) }.name,
                opponent3Name = roomPlayers.getOrElse(1) { DoudizhuRoomHost.PlayerInfo("", "雀士A", 1, 0) }.name,
                playerName = playerName,
                opponent1HandSize = state.handSizes.getOrElse(3) { 0 },
                opponent2HandSize = state.handSizes.getOrElse(2) { 0 },
                opponent3HandSize = state.handSizes.getOrElse(1) { 0 },
                myPoints = myPoints,
                myTiles = mahjongHost?.engine?.getHand(0) ?: emptyList(),
                myMelds = mahjongHost?.engine?.getMelds(0) ?: emptyList(),
                drawnTile = state.drawnTile,
                selectedTile = selectedTile,
                isMyTurn = isMyDraw || isMyDiscard,
                lastDiscard = state.lastDiscard,
                lastDiscardPlayer = state.lastDiscardPlayer,
                melds1 = mahjongHost?.engine?.getMelds(1) ?: emptyList(),
                melds2 = mahjongHost?.engine?.getMelds(2) ?: emptyList(),
                melds3 = mahjongHost?.engine?.getMelds(3) ?: emptyList(),
                canHu = mahjongResponses?.get(0)?.canHu == true,
                canGang = mahjongResponses?.get(0)?.canGang == true,
                canPeng = mahjongResponses?.get(0)?.canPeng == true,
                canChi = mahjongResponses?.get(0)?.canChi == true,
                isResponsePhase = isMyResponse && canRespond,
                onTileClick = { tile ->
                    if (isMyDiscard) {
                        selectedTile = if (selectedTile == tile) null else tile
                    }
                },
                onDraw = { mahjongHost?.hostDraw() },
                onDiscard = {
                    val tile = selectedTile
                    if (tile != null) { mahjongHost?.hostDiscard(tile); selectedTile = null }
                },
                onHu = { mahjongHost?.hostResponse("hu") },
                onGang = { mahjongHost?.hostResponse("gang") },
                onPeng = { mahjongHost?.hostResponse("peng") },
                onChi = { mahjongHost?.hostResponse("chi") },
                onPass = { mahjongHost?.hostResponse("pass") },
                onLeave = { leaveRoom() },
                gameOverMsg = gameOverMsg,
                onContinue = { gameOverMsg = null; screen = Screen.LOBBY }
            )
        }
    }
}

enum class Screen { LOBBY, DOUDIZHU_ROOM, DOUDIZHU_GAME, MAHJONG_ROOM, MAHJONG_GAME }