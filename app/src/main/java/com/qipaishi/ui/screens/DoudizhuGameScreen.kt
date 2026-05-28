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
import com.qipaishi.game.doudizhu.engine.GamePhase
import com.qipaishi.game.doudizhu.engine.PlayedCards
import com.qipaishi.game.doudizhu.model.Card
import com.qipaishi.ui.theme.*

/**
 * 斗地主牌桌页面 — 实际可交互版
 */
@Composable
fun DoudizhuGameScreen(
    opponent1Name: String = "对手1",
    opponent2Name: String = "对手2",
    opponent1HandSize: Int = 17,
    opponent2HandSize: Int = 17,
    myPoints: Int = 10000,
    myCards: List<Card> = emptyList(),
    myIsLandlord: Boolean = false,
    opponent1IsLandlord: Boolean = false,
    opponent2IsLandlord: Boolean = false,
    isMyTurn: Boolean = false,
    canPass: Boolean = false,
    bottomCards: List<String> = emptyList(),
    isBiddingPhase: Boolean = false,
    currentBidder: Int = -1,
    bidMultiplier: Int = 1,
    bombCount: Int = 0,
    currentTurnPlayer: Int = -1,
    landlordIndex: Int = -1,
    selectedCards: Set<Card> = emptySet(),
    lastPlay: PlayedCards? = null,
    onCardToggle: (Card) -> Unit = {},
    onPlay: () -> Unit = {},
    onPass: () -> Unit = {},
    onBid: (Int) -> Unit = {},
    onHint: () -> Unit = {},
    onLeave: () -> Unit = {},
    gameOverMsg: String? = null,
    onContinue: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().background(TableGreen)) {
        // 顶部信息栏
        Row(modifier = Modifier.fillMaxWidth().background(TableGreenDark)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("叫分: ${bidMultiplier}倍", color = Gold, fontSize = 13.sp)
            Text("💣 $bombCount", color = CardWhite.copy(alpha = 0.7f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("底牌: ", color = CardWhite.copy(alpha = 0.5f), fontSize = 12.sp)
                if (bottomCards.isNotEmpty()) {
                    bottomCards.forEach { Text(it, color = CardWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp)) }
                }
            }
        }

        // 牌桌区
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
            // 对手2（上）
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                   horizontalAlignment = Alignment.CenterHorizontally) {
                OpponentCard2(name = opponent2Name, handSize = opponent2HandSize, isLandlord = opponent2IsLandlord,
                    isMyTurn = currentTurnPlayer == 2, isBidding = currentBidder == 2 && isBiddingPhase)
            }
            // 对手1（左）
            Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                OpponentCard2(name = opponent1Name, handSize = opponent1HandSize, isLandlord = opponent1IsLandlord,
                    isMyTurn = currentTurnPlayer == 1, isBidding = currentBidder == 1 && isBiddingPhase)
            }
            // 中间出牌区
            lastPlay?.let { play ->
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(play.group.cards.joinToString(" ") { it.display },
                        color = CardWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(play.group.type.description, color = Gold, fontSize = 12.sp)
                }
            }
        }

        // 叫地主面板
        if (isBiddingPhase && currentBidder == 0) {
            Row(modifier = Modifier.fillMaxWidth().background(TableGreenDark).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { onBid(0) }, colors = ButtonDefaults.buttonColors(containerColor = CardWhite.copy(alpha = 0.15f)),
                       shape = RoundedCornerShape(10.dp)) { Text("不叫", color = CardWhite, fontSize = 16.sp) }
                Button(onClick = { onBid(1) }, colors = ButtonDefaults.buttonColors(containerColor = Green600),
                       shape = RoundedCornerShape(10.dp)) { Text("1分", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Button(onClick = { onBid(2) }, colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                       shape = RoundedCornerShape(10.dp)) { Text("2分", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Button(onClick = { onBid(3) }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                       shape = RoundedCornerShape(10.dp)) { Text("3分 🔥", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // 底部操作区
        Column(modifier = Modifier.fillMaxWidth().background(TableGreenDark.copy(alpha = 0.8f)).padding(vertical = 8.dp)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                items(myCards.size) { i ->
                    val card = myCards[i]
                    val isRed = card.suit in listOf(Card.Suit.HEART, Card.Suit.DIAMOND, Card.Suit.JOKER)
                    val textColor = if (isRed) CardRed else CardBlack
                    val isSel = card in selectedCards
                    Box(
                        modifier = Modifier.width(44.dp).height(64.dp)
                            .offset(y = if (isSel) (-12).dp else 0.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) CardSelected else CardWhite)
                            .border(1.dp, if (isSel) GoldDark else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable { onCardToggle(card) }
                            .padding(4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(card.display, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(card.suit.symbol, modifier = Modifier.align(Alignment.CenterHorizontally), color = textColor, fontSize = 16.sp)
                            Text(card.display, modifier = Modifier.align(Alignment.End), color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onLeave,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text("退出", color = ErrorRed, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onPass, enabled = isMyTurn && canPass,
                    colors = ButtonDefaults.buttonColors(containerColor = CardWhite.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp)) { Text("不出", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Button(onClick = onPlay,
                    enabled = isMyTurn && selectedCards.isNotEmpty() && !isBiddingPhase,
                    colors = ButtonDefaults.buttonColors(containerColor = SelectedCardsColor),
                    shape = RoundedCornerShape(10.dp)) {
                    Text("出牌 (${selectedCards.size})", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 游戏结束对话框
    gameOverMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = TableGreenDark,
            shape = RoundedCornerShape(16.dp),
            title = { Text("🎉 游戏结束", color = Gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = CardWhite, textAlign = TextAlign.Center, fontSize = 16.sp) },
            confirmButton = {
                Button(onClick = { onContinue(); onLeave() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDark), shape = RoundedCornerShape(10.dp)) {
                    Text("返回大厅", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
fun OpponentCard2(name: String, handSize: Int, isLandlord: Boolean, isMyTurn: Boolean, isBidding: Boolean) {
    Card(
        modifier = Modifier.width(120.dp).then(if (isMyTurn || isBidding) Modifier.border(2.dp, Gold, RoundedCornerShape(12.dp)) else Modifier),
        colors = CardDefaults.cardColors(containerColor = if (isMyTurn) Green700 else Green700.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = CardWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (isLandlord) { Spacer(Modifier.width(4.dp)); Text("👑", fontSize = 16.sp) }
            }
            Text("${handSize}张", color = CardWhite.copy(alpha = 0.6f), fontSize = 12.sp)
            if (isBidding) Text("🤔 叫地主中...", color = Gold, fontSize = 11.sp)
        }
    }
}

private val SelectedCardsColor = Color(0xFFB8860B)