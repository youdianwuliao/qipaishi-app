package com.qipaishi.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qipaishi.game.doudizhu.model.Card
import com.qipaishi.ui.theme.*

/**
 * 斗地主牌桌页面
 *
 * 布局：
 * ┌──────────────────────────┐
 * │       对手 2              │
 * │   手牌数:7  出的牌: K♦K♠   │
 * │                           │
 * │ 对手1             底牌    │
 * │ 手牌数:5          3张     │
 * │                           │
 * │      你的手牌             │
 * │  ♠3 ♥4 ♣5 ♦6 ...        │
 * │  [不出] [提示] [出牌]      │
 * └──────────────────────────┘
 */
@Composable
fun DoudizhuGameScreen(
    // 对手信息
    opponent1Name: String = "对手1",
    opponent1HandSize: Int = 17,
    opponent1Points: Int = 10000,
    opponent1PlayedCards: List<String> = emptyList(),
    opponent1PlayedType: String? = null,
    opponent1IsLandlord: Boolean = false,

    opponent2Name: String = "对手2",
    opponent2HandSize: Int = 17,
    opponent2Points: Int = 10000,
    opponent2PlayedCards: List<String> = emptyList(),
    opponent2PlayedType: String? = null,
    opponent2IsLandlord: Boolean = false,

    // 自己的信息
    myName: String = "你",
    myPoints: Int = 10000,
    myCards: List<Card> = emptyList(),
    myIsLandlord: Boolean = false,
    isMyTurn: Boolean = false,
    canPass: Boolean = false,

    // 底牌
    bottomCards: List<String> = emptyList(),

    // 叫地主
    isBiddingPhase: Boolean = false,
    currentBidder: Int = -1,  // 当前叫地主的人（0=你, 1=对手1, 2=对手2）
    bidMultiplier: Int = 1,
    bombCount: Int = 0,

    // 游戏状态
    currentTurnPlayer: Int = -1,  // 0=你, 1=对手1, 2=对手2
    landlordIndex: Int = -1,

    // 互动
    onCardToggle: (Card) -> Unit = {},
    onPlay: () -> Unit = {},
    onPass: () -> Unit = {},
    onBid: (Int) -> Unit = {},
    onHint: () -> Unit = {},
    selectedCards: Set<Card> = emptySet(),

    // 游戏结束
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
        // 顶部信息栏
        TopInfoBar(
            bidMultiplier = bidMultiplier,
            bombCount = bombCount,
            bottomCards = bottomCards,
            landlordIndex = landlordIndex
        )

        // 牌桌区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            // 对手 2（上方中间）
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OpponentCard(
                    name = opponent2Name,
                    handSize = opponent2HandSize,
                    points = opponent2Points,
                    isLandlord = opponent2IsLandlord,
                    playedCards = opponent2PlayedCards,
                    playedType = opponent2PlayedType,
                    isCurrentTurn = currentTurnPlayer == 2,
                    isBidding = currentBidder == 2 && isBiddingPhase
                )
            }

            // 对手 1（左侧）
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OpponentCard(
                    name = opponent1Name,
                    handSize = opponent1HandSize,
                    points = opponent1Points,
                    isLandlord = opponent1IsLandlord,
                    playedCards = opponent1PlayedCards,
                    playedType = opponent1PlayedType,
                    isCurrentTurn = currentTurnPlayer == 1,
                    isBidding = currentBidder == 1 && isBiddingPhase
                )
            }

            // 自己的出牌区（中间）
            if (myCards.isEmpty()) {
                Text(
                    "开局中...",
                    modifier = Modifier.align(Alignment.Center),
                    color = CardWhite.copy(alpha = 0.5f),
                    fontSize = 18.sp
                )
            }
        }

        // 叫地主 UI
        if (isBiddingPhase && currentBidder == 0) {
            BiddingPanel(
                modifier = Modifier.fillMaxWidth(),
                onBid = onBid
            )
        }

        // 底部操作区
        if (!isBiddingPhase && myCards.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TableGreenDark.copy(alpha = 0.8f))
                    .padding(vertical = 8.dp)
            ) {
                // 手牌
                HandCards(
                    cards = myCards,
                    selectedCards = selectedCards,
                    onCardToggle = onCardToggle
                )

                // 出牌按钮
                PlayButtons(
                    isMyTurn = isMyTurn,
                    canPass = canPass,
                    selectedCount = selectedCards.size,
                    onPlay = onPlay,
                    onPass = onPass,
                    onHint = onHint
                )
            }
        }
    }

    // 游戏结束弹窗
    if (isGameOver) {
        GameOverDialog(
            message = gameOverMessage,
            onContinue = onContinue,
            onLeave = onLeave
        )
    }
}

@Composable
fun TopInfoBar(
    bidMultiplier: Int,
    bombCount: Int,
    bottomCards: List<String>,
    landlordIndex: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TableGreenDark)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("叫分: ${bidMultiplier}倍", color = Gold, fontSize = 13.sp)
        Text("炸弹: $bombCount", color = CardWhite.copy(alpha = 0.7f), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("底牌: ", color = CardWhite.copy(alpha = 0.5f), fontSize = 12.sp)
            if (bottomCards.isNotEmpty()) {
                bottomCards.forEach { card ->
                    Text(card, color = CardWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                }
            } else {
                Text("???", color = CardWhite.copy(alpha = 0.3f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun OpponentCard(
    name: String,
    handSize: Int,
    points: Int,
    isLandlord: Boolean,
    playedCards: List<String>,
    playedType: String?,
    isCurrentTurn: Boolean,
    isBidding: Boolean
) {
    val borderColor = when {
        isCurrentTurn -> Gold
        isBidding -> Gold.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .width(140.dp)
            .then(
                if (isCurrentTurn || isBidding)
                    Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTurn) Green700 else Green700.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 名字 + 地主标记
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = CardWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (isLandlord) {
                    Spacer(Modifier.width(4.dp))
                    Text("👑", fontSize = 16.sp)
                }
            }
            Text("🪙 $points", color = Gold, fontSize = 12.sp)

            Spacer(Modifier.height(4.dp))

            // 手牌数
            Text("${handSize}张", color = CardWhite.copy(alpha = 0.6f), fontSize = 12.sp)

            // 出的牌
            if (playedCards.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Green600)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        playedCards.joinToString(" "),
                        color = CardWhite,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
                if (playedType != null) {
                    Text(playedType, color = CardWhite.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }

            // 叫地主标记
            if (isBidding) {
                Spacer(Modifier.height(4.dp))
                Text("🤔 叫地主中...", color = Gold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun HandCards(
    cards: List<Card>,
    selectedCards: Set<Card>,
    onCardToggle: (Card) -> Unit
) {
    // 水平滚动手牌
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy((-8).dp)  // 牌叠在一起
    ) {
        items(cards.size) { index ->
            CardView(
                card = cards[index],
                isSelected = cards[index] in selectedCards,
                onClick = { onCardToggle(cards[index]) }
            )
        }
    }
}

@Composable
fun CardView(
    card: Card,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isRed = card.suit in listOf(Card.Suit.HEART, Card.Suit.DIAMOND) || card.isJoker
    val textColor = if (isRed) CardRed else CardBlack

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(64.dp)
            .offset(y = if (isSelected) (-12).dp else 0.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) CardSelected else CardWhite)
            .border(1.dp, if (isSelected) GoldDark else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 左上角：点数 + 花色
            Text(
                card.display,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            // 中间花色
            Text(
                card.suit.symbol,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = textColor,
                fontSize = 16.sp
            )

            // 右下角（倒过来）
            Text(
                card.display,
                modifier = Modifier.align(Alignment.End),
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PlayButtons(
    isMyTurn: Boolean,
    canPass: Boolean,
    selectedCount: Int,
    onPlay: () -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPass,
            modifier = Modifier.weight(1f),
            enabled = isMyTurn && canPass,
            colors = ButtonDefaults.buttonColors(
                containerColor = CardWhite.copy(alpha = 0.15f),
                disabledContainerColor = CardWhite.copy(alpha = 0.05f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("不出", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onHint,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Green600),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("💡 提示", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f),
            enabled = isMyTurn && selectedCount > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldDark,
                disabledContainerColor = GoldDark.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                if (selectedCount > 0) "出牌 ($selectedCount)" else "出牌",
                color = CardWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BiddingPanel(
    modifier: Modifier = Modifier,
    onBid: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .background(TableGreenDark)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = { onBid(0) },
            colors = ButtonDefaults.buttonColors(containerColor = CardWhite.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("不叫", color = CardWhite, fontSize = 16.sp)
        }

        Button(
            onClick = { onBid(1) },
            colors = ButtonDefaults.buttonColors(containerColor = Green600),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("1分", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { onBid(2) },
            colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("2分", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { onBid(3) },
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("3分 🔥", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GameOverDialog(
    message: String,
    onContinue: () -> Unit,
    onLeave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = TableGreenDark,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "🎉 游戏结束",
                color = Gold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                message,
                color = CardWhite,
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("继续下一局", color = CardWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("退出房间", color = CardWhite.copy(alpha = 0.5f), fontSize = 14.sp)
            }
        }
    )
}