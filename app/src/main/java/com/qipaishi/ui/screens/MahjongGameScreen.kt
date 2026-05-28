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

@Composable
fun MahjongGameScreen(
    opponent1Name: String = "北",
    opponent2Name: String = "西",
    opponent3Name: String = "南",
    playerName: String = "东",
    opponent1HandSize: Int = 13,
    opponent2HandSize: Int = 13,
    opponent3HandSize: Int = 13,
    myPoints: Int = 10000,
    myTiles: List<Tile> = emptyList(),
    myMelds: List<Meld> = emptyList(),
    drawnTile: Tile? = null,
    selectedTile: Tile? = null,
    isMyTurn: Boolean = false,
    lastDiscard: Tile? = null,
    lastDiscardPlayer: Int = -1,
    melds1: List<Meld> = emptyList(),
    melds2: List<Meld> = emptyList(),
    melds3: List<Meld> = emptyList(),
    canHu: Boolean = false,
    canGang: Boolean = false,
    canPeng: Boolean = false,
    canChi: Boolean = false,
    isResponsePhase: Boolean = false,
    onTileClick: (Tile) -> Unit = {},
    onDraw: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onHu: () -> Unit = {},
    onGang: () -> Unit = {},
    onPeng: () -> Unit = {},
    onChi: () -> Unit = {},
    onPass: () -> Unit = {},
    onLeave: () -> Unit = {},
    gameOverMsg: String? = null,
    onContinue: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().background(TableGreen)) {
        // 顶部信息栏
        Row(modifier = Modifier.fillMaxWidth().background(TableGreenDark).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            if (lastDiscard != null) Text("打出: ${tileDisplay(lastDiscard)}", color = Gold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (drawnTile != null && isMyTurn) Text("摸到: ${tileDisplay(drawnTile)}", color = CardWhite, fontSize = 13.sp)
        }

        // 牌桌
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 对家（上）
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OpponentTileView(opponent2Name, opponent2HandSize, melds2)
            }
            // 左
            Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                OpponentTileView(opponent1Name, opponent1HandSize, melds1)
            }
            // 右
            Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                OpponentTileView(opponent3Name, opponent3HandSize, melds3)
            }
            // 中央
            if (lastDiscard != null) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tileDisplay(lastDiscard), color = CardWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("玩家${lastDiscardPlayer+1}打出", color = CardWhite.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }

        // 我的副露
        if (myMelds.isNotEmpty()) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                items(myMelds) { meld ->
                    Text(meld.tiles.joinToString("") { tileDisplay(it) },
                        color = CardWhite.copy(alpha = 0.6f), fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp))
                }
            }
        }

        // 我的手牌
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
            items(myTiles.size) { i ->
                val tile = myTiles[i]
                val isSel = selectedTile == tile
                val isDrawn = drawnTile == tile
                Box(modifier = Modifier.width(40.dp).height(56.dp)
                    .offset(y = if (isSel) (-10).dp else 0.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(when { isSel -> CardSelected; isDrawn -> GoldDark; else -> CardWhite })
                    .border(1.dp, if (isSel) Gold else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                    .clickable(enabled = isMyTurn) { onTileClick(tile) }
                    .padding(3.dp),
                    contentAlignment = Alignment.Center) {
                    Text(tileDisplay(tile), color = TileTextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 操作按钮
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            if (isResponsePhase) {
                Button(onClick = onHu, enabled = canHu,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(10.dp)) {
                    Text("胡", color = CardWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onGang, enabled = canGang,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDark), shape = RoundedCornerShape(10.dp)) {
                    Text("杠", color = CardWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onPeng, enabled = canPeng,
                    colors = ButtonDefaults.buttonColors(containerColor = Green600), shape = RoundedCornerShape(10.dp)) {
                    Text("碰", color = CardWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onChi, enabled = canChi,
                    colors = ButtonDefaults.buttonColors(containerColor = CardWhite.copy(alpha = 0.2f)), shape = RoundedCornerShape(10.dp)) {
                    Text("吃", color = CardWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onPass,
                    colors = ButtonDefaults.buttonColors(containerColor = CardWhite.copy(alpha = 0.15f)), shape = RoundedCornerShape(10.dp)) {
                    Text("过", color = CardWhite, fontSize = 14.sp)
                }
            } else if (isMyTurn) {
                Button(onClick = onDraw, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDark), shape = RoundedCornerShape(10.dp)) {
                    Text("摸牌", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onDiscard, modifier = Modifier.weight(1f), enabled = selectedTile != null,
                    colors = ButtonDefaults.buttonColors(containerColor = SelectedCardsColor), shape = RoundedCornerShape(10.dp)) {
                    Text("打牌", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.weight(1f))
                Text("等待其他玩家...", color = CardWhite.copy(alpha = 0.4f), fontSize = 14.sp)
            }

            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onLeave, colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("退出", fontSize = 12.sp)
            }
        }
    }

    // 游戏结束
    gameOverMsg?.let { msg ->
        AlertDialog(onDismissRequest = {},
            containerColor = TableGreenDark, shape = RoundedCornerShape(16.dp),
            title = { Text("🀄 游戏结束", color = Gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = CardWhite, textAlign = TextAlign.Center, fontSize = 16.sp) },
            confirmButton = { Button(onClick = { onContinue(); onLeave() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark), shape = RoundedCornerShape(10.dp)) {
                Text("返回大厅", color = CardWhite, fontWeight = FontWeight.Bold)
            } },
            dismissButton = {}
        )
    }
}

@Composable
fun OpponentTileView(name: String, handSize: Int, melds: List<Meld>) {
    Card(modifier = Modifier.width(110.dp),
        colors = CardDefaults.cardColors(containerColor = Green700.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = CardWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${handSize}张", color = CardWhite.copy(alpha = 0.6f), fontSize = 11.sp)
            if (melds.isNotEmpty()) {
                melds.forEach { meld ->
                    Text(meld.tiles.joinToString("") { tileDisplay(it) },
                        color = CardWhite.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        }
    }
}

private fun tileDisplay(tile: Tile): String {
    return when (tile.suit) {
        Tile.Suit.WAN -> "${tile.value}万"
        Tile.Suit.TIAO -> "${tile.value}条"
        Tile.Suit.BING -> "${tile.value}饼"
        Tile.Suit.FENG -> listOf("东","南","西","北")[tile.value - 1]
        Tile.Suit.JIAN -> listOf("中","发","白")[tile.value - 1]
    }
}

private val TileTextColor = Color(0xFF333333)
private val SelectedCardsColor = Color(0xFFB8860B)

data class MahjongPlayerData(
    val name: String, val index: Int, val points: Int,
    val hand: List<Tile>, val melds: List<Meld>,
    val isYou: Boolean, val isCurrent: Boolean
)