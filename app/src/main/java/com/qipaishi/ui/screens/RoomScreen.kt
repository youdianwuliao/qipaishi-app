package com.qipaishi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qipaishi.ui.theme.*

/**
 * 房间等待页面
 *
 * 显示玩家列表，满员自动开始。
 */
@Composable
fun RoomScreen(
    roomName: String = "张三的牌桌",
    players: List<RoomPlayerInfo> = listOf(
        RoomPlayerInfo("你", 0, 10000, true),
        RoomPlayerInfo("等待中...", 1, 0, false),
        RoomPlayerInfo("等待中...", 2, 0, false)
    ),
    onLeave: () -> Unit = {}
) {
    val isFull = players.all { it.joined }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TableGreen)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // 房间标题
        Text("🃏", fontSize = 56.sp)
        Text(
            roomName,
            color = CardWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (isFull) "即将开始..." else "等待玩家加入...",
            color = if (isFull) Gold else CardWhite.copy(alpha = 0.5f),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(32.dp))

        // 玩家座位
        // 座位布局：上面对手，下面自己
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 对手 2（座位 2）
            if (players.size > 2) {
                PlayerSeat(players[2], isYou = false)
            }

            // 对手 1（座位 1）
            if (players.size > 1) {
                PlayerSeat(players[1], isYou = false)
            }

            Spacer(Modifier.height(8.dp))

            // 你自己（座位 0）
            Divider(color = CardWhite.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))
            PlayerSeat(players[0], isYou = true, isSelf = true)
        }

        Spacer(Modifier.weight(1f))

        // 提示文字
        if (isFull) {
            Text(
                "3 人已就位，开始发牌！",
                color = Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                "需要 ${3 - players.count { it.joined }} 人加入",
                color = CardWhite.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // 离开按钮
        OutlinedButton(
            onClick = onLeave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("离开房间", fontSize = 16.sp)
        }
    }
}

@Composable
fun PlayerSeat(
    player: RoomPlayerInfo,
    isYou: Boolean,
    isSelf: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelf) Green700.copy(alpha = 0.8f) else Green700.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (player.joined) Green600 else CardWhite.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (player.joined) "👤" else "❓",
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        color = if (player.joined) CardWhite else CardWhite.copy(alpha = 0.4f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isYou) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Gold)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("你", color = TableGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (player.joined) {
                    Text("🪙 ${player.points} 分", color = Gold, fontSize = 13.sp)
                }
            }

            // 状态
            if (player.joined) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuccessGreen)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("已就位", color = CardWhite, fontSize = 12.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardWhite.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("等待中", color = CardWhite.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
        }
    }
}

data class RoomPlayerInfo(
    val name: String,
    val index: Int,
    val points: Int,
    val joined: Boolean
)