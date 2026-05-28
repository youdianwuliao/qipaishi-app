package com.qipaishi.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qipaishi.game.mahjong.model.*
import com.qipaishi.ui.theme.*

/**
 * 麻将牌桌页面
 *
 * 布局（4 个方位）：
 *       对手2（对家）
 * 对手1          对手3
 *       你（下家）
 */
@Composable
fun MahjongGameScreen(
    players: List<MahjongPlayerData> = listOf(
        MahjongPlayerData("你", 0, 10000, emptyList(), emptyList(), true, false),
        MahjongPlayerData("南", 1, 10000, emptyList(), emptyList(), false, false),
        MahjongPlayerData("西", 2, 10000, emptyList(), emptyList(), false, false),
        MahjongPlayerData("北", 3, 10000, emptyList(), emptyList(), false, false)
    ),
    isMyTurn: Boolean = false,
    canActions: PlayerResponses? = null,  // 可做的操作
    lastDiscard: String = "",
    wallRemaining: Int = 70,
    myIndex: Int = 0,
    onDiscard: (Tile) -> Unit = {},
    onHu: () -> Unit = {},
    onPeng: () -> Unit = {},
    onGang: () -> Unit = {},
    onChi: (List<Tile>) -> Unit = {},
    onPass: () -> Unit = {},
    isGameOver: Boolean = false,
    gameOverMessage: String = "",
    onContinue: () -> Unit = {},
    onLeave: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TableGreen)
    ) {
        // 顶部：剩余牌数 + 信息栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TableGreenDark)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🀄 剩余: $wallRemaining 张", color = CardWhite.copy(alpha = 0.7f), fontSize = 13.sp)
            if (lastDiscard.isNotEmpty()) {
                Text("已打出: $lastDiscard", color = Gold, fontSize = 13.sp)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 对家（上方）
            PlayerInfoCompact(
                player = players[2],
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )

            // 左家
            Column(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
            ) {
                PlayerInfoCompact(player = players[1])
            }

            // 右家
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
            ) {
                PlayerInfoCompact(player = players[3])
            }

            // 中央信息
            if (lastDiscard.isNotEmpty() && canActions != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(lastDiscard, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("等待操作...", color = CardWhite.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }

        // 自己手牌 + 副露 + 操作按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TableGreenDark.copy(alpha = 0.8f))
                .padding(vertical = 8.dp)
        ) {
            // 副露区
            if (players[myIndex].melds.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(players[myIndex].melds) { meld ->
                        MeldView(meld)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 手牌
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                items(players[myIndex].hand) { tile ->
                    MahjongTileView(tile = tile, onClick = { onDiscard(tile) })
                }
            }

            // 操作按钮
            if (canActions != null) {
                Spacer(Modifier.height(8.dp))
                MahjongActionButtons(
                    canActions = canActions,
                    onHu = onHu,
                    onPeng = onPeng,
                    onGang = onGang,
                    onChi = onChi,
                    onPass = onPass
                )
            }
        }
    }

    if (isGameOver) {
        GameOverDialog(message = gameOverMessage, onContinue = onContinue, onLeave = onLeave)
    }
}

@Composable
fun PlayerInfoCompact(
    player: MahjongPlayerData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (player.isCurrentTurn) Green700 else Green700.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(player.name, color = CardWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (player.isDealer) {
                    Text("庄", color = Gold, fontSize = 11.sp)
                }
            }
            Text("${player.hand.size}张 | 🪙${player.points}",
                color = CardWhite.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
fun MahjongTileView(
    tile: Tile,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val bgColor = when (tile.suit) {
        Tile.Suit.WAN -> Color(0xFFFFF8E1)
        Tile.Suit.TIAO -> Color(0xFFF0FFF0)
        Tile.Suit.BING -> Color(0xFFF0F8FF)
        Tile.Suit.FENG -> Color(0xFFF5F0FF)
        Tile.Suit.JIAN -> Color(0xFFFFF0F0)
    }

    Box(
        modifier = Modifier
            .width(38.dp)
            .height(52.dp)
            .offset(y = if (isSelected) (-8).dp else 0.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) CardSelected else bgColor)
            .border(1.dp, if (isSelected) Gold else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tile.display,
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MeldView(meld: Meld) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Green600.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text("${meld.type.description}:", color = Gold, fontSize = 10.sp)
        meld.tiles.forEach { tile ->
            Text(tile.display, color = CardWhite, fontSize = 12.sp)
        }
    }
}

@Composable
fun MahjongActionButtons(
    canActions: PlayerResponses,
    onHu: () -> Unit,
    onPeng: () -> Unit,
    onGang: () -> Unit,
    onChi: (List<Tile>) -> Unit,
    onPass: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (canActions.canHu) {
            Button(
                onClick = onHu,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(8.dp)
            ) { Text("胡！", color = CardWhite, fontWeight = FontWeight.Bold) }
        }
        if (canActions.canGang) {
            Button(
                onClick = onGang,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                shape = RoundedCornerShape(8.dp)
            ) { Text("杠", color = CardWhite, fontWeight = FontWeight.Bold) }
        }
        if (canActions.canPeng) {
            Button(
                onClick = onPeng,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Green600),
                shape = RoundedCornerShape(8.dp)
            ) { Text("碰", color = CardWhite, fontWeight = FontWeight.Bold) }
        }
        if (canActions.canChi) {
            Button(
                onClick = { onChi(emptyList()) },  // 简化
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Green700),
                shape = RoundedCornerShape(8.dp)
            ) { Text("吃", color = CardWhite, fontWeight = FontWeight.Bold) }
        }
        Button(
            onClick = onPass,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = CardWhite.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(8.dp)
        ) { Text("过", color = CardWhite) }
    }
}

data class MahjongPlayerData(
    val name: String,
    val index: Int,
    val points: Int,
    val hand: List<Tile>,
    val melds: List<Meld>,
    val isCurrentTurn: Boolean,
    val isDealer: Boolean
)