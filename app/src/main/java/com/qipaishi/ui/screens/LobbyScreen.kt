package com.qipaishi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.qipaishi.network.RoomScanner
import com.qipaishi.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

/**
 * 大厅页面
 *
 * - 顶部：昵称 + 积分
 * - 中间：局域网房间列表（自动刷新）
 * - 底部：创建斗地主 / 创建麻将
 */
@Composable
fun LobbyScreen(
    playerName: String = "玩家",
    points: Int = 10000,
    onNavigateToRoom: (roomId: String) -> Unit = {},
    onCreateRoom: () -> Unit = {}
) {
    val scanner = remember { RoomScanner() }
    val rooms by scanner.rooms.collectAsState()

    LaunchedEffect(Unit) {
        scanner.start(this)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TableGreen)
            .padding(16.dp)
    ) {
        // 顶部：头像 + 昵称 + 积分
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像占位
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold),
                contentAlignment = Alignment.Center
            ) {
                Text("🀄", fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(playerName, color = CardWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙 ", fontSize = 14.sp)
                    Text("$points 分", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 标题
        Text(
            "🏠 局域网房间",
            color = CardWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "同一 Wi-Fi 下自动发现",
            color = CardWhite.copy(alpha = 0.6f),
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        // 房间列表
        if (rooms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("扫描中...", color = CardWhite.copy(alpha = 0.5f), fontSize = 16.sp)
                    Text("等待局域网房间出现", color = CardWhite.copy(alpha = 0.3f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rooms.values.toList()) { roomState ->
                    RoomCard(
                        roomInfo = roomState.roomInfo,
                        onJoin = { onNavigateToRoom(roomState.roomInfo.roomId) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 底部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCreateRoom,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green700),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🃏 创建斗地主", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { /* 麻将房间创建 */ },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🀄 创建麻将", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RoomCard(
    roomInfo: com.qipaishi.network.protocol.RoomInfo,
    onJoin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Green700.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 游戏图标
            val icon = if (roomInfo.game == "doudizhu") "🃏" else "🀄"
            val gameName = if (roomInfo.game == "doudizhu") "斗地主" else "麻将"

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Green600),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 24.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    roomInfo.hostName,
                    color = CardWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    gameName,
                    color = CardWhite.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }

            // 人数标签
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (roomInfo.playerCount < roomInfo.maxPlayers) Green600 else ErrorRed)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "${roomInfo.playerCount}/${roomInfo.maxPlayers}",
                    color = CardWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onJoin,
                enabled = roomInfo.playerCount < roomInfo.maxPlayers,
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("加入", color = CardWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}