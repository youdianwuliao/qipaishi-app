package com.qipaishi.network.protocol

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * === 通信协议 ===
 *
 * 所有消息序列化为 JSON，每行一条（以 \n 结尾）
 *
 * 格式：{ "type": "MESSAGE_TYPE", "data": { ... } }
 */

@Serializable
data class GameMessage(
    val type: String,
    val data: JsonElement? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(line: String): GameMessage = json.decodeFromString(line)

        fun toJson(msg: GameMessage): String = json.encodeToString(msg) + "\n"

        fun create(type: String, data: Any? = null): GameMessage {
            return GameMessage(type, if (data != null) json.encodeToJsonElement(data) else null)
        }
    }
}

// ================================================================
// 房间发现（UDP）
// ================================================================

@Serializable
data class RoomInfo(
    val roomId: String,         // 唯一房间 ID
    val hostName: String,       // 房主昵称
    val game: String,           // "doudizhu" / "mahjong"
    val playerCount: Int,       // 当前人数
    val maxPlayers: Int,        // 3（斗地主）/ 4（麻将）
    val hostAddress: String     // 房主的局域网 IP（用于 TCP 连接）
)

// ================================================================
// 房间消息（TCP）
// ================================================================

// → 加入房间
@Serializable
data class JoinRequest(val playerId: String, val playerName: String, val points: Int)

// → 玩家加入通知
@Serializable
data class PlayerJoined(val playerId: String, val playerName: String, val playerIndex: Int, val points: Int)

// → 准备/取消准备
@Serializable
data class ReadyRequest(val ready: Boolean)

// → 每个玩家的准备状态
@Serializable
data class PlayerReadyState(val playerId: String, val ready: Boolean)

// → 全局游戏状态同步（序列化整个 GameState）
@Serializable
data class SyncState(
    val phase: String,               // "BIDDING" / "PLAYING" / "GAME_OVER"
    val currentPlayerIndex: Int,
    val landlordIndex: Int,          // -1 未确定
    val handSizes: List<Int>,        // 所有玩家手牌数（对手隐藏内容）
    val myCards: List<String>,        // 自己的手牌（仅对自己发送）
    val bottomCards: List<String>,    // 底牌显示
    val lastPlayedCards: List<String>?,       // 上一轮出的牌
    val lastPlayedType: String?,              // 上一轮牌型描述
    val lastPlayedBy: Int?,                   // 上一轮谁出的
    val bidMultiplier: Int,
    val bombCount: Int,
    val winner: Int?,
    val scores: Map<Int, Int>?       // 结算结果
)

// ================================================================
// 斗地主消息
// ================================================================

// 叫地主
@Serializable
data class BidAction(val playerIndex: Int, val score: Int)

// 出牌
@Serializable
data class PlayAction(val playerIndex: Int, val cardRanks: List<Int>, val cardSuits: List<String>)

// 不出
@Serializable
data class PassAction(val playerIndex: Int)

// 出牌结果
@Serializable
data class PlayResult(
    val accepted: Boolean,
    val reason: String? = null,
    val isBomb: Boolean = false
)

// 游戏结束
@Serializable
data class GameOverInfo(
    val winner: Int,                // 胜者下标
    val winnerSide: String,         // "LANDLORD" / "FARMER"
    val scores: Map<Int, Int>,      // 积分变化
    val finalMultiplier: Int
)

// ================================================================
// 通用消息
// ================================================================

// 聊天消息（快捷语）
@Serializable
data class ChatMessage(val playerIndex: Int, val text: String)

// 错误消息
@Serializable
data class ErrorInfo(val code: Int, val message: String)

// ================================================================
// 消息类型常量
// ================================================================

object MessageType {
    // 房间
    const val JOIN = "JOIN"
    const val PLAYER_JOINED = "PLAYER_JOINED"
    const val READY = "READY"
    const val READY_STATE = "READY_STATE"
    const val PLAYER_LEAVE = "PLAYER_LEAVE"
    const val ROOM_DESTROYED = "ROOM_DESTROYED"
    const val SYNC_STATE = "SYNC_STATE"

    // 斗地主
    const val BID = "BID"
    const val PLAY = "PLAY"
    const val PASS = "PASS"
    const val PLAY_RESULT = "PLAY_RESULT"
    const val GAME_OVER = "GAME_OVER"
    const val RESTART = "RESTART"

    // 通用
    const val CHAT = "CHAT"
    const val ERROR = "ERROR"
}