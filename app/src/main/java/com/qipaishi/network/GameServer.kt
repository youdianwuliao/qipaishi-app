package com.qipaishi.network

import com.qipaishi.network.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * TCP 游戏服务器（房主端）
 *
 * 管理所有玩家连接，分发游戏消息。
 * 监听指定端口，接受客户端连接。
 */
class GameServer(
    private val port: Int,
    private val maxPlayers: Int,
    private val myPlayerId: String,
    private val myPlayerName: String,
    private val myPoints: Int
) {
    private var serverSocket: ServerSocket? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val clients = ConcurrentHashMap<String, ClientConnection>()

    private val _messages = MutableSharedFlow<IncomingMessage>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<IncomingMessage> = _messages.asSharedFlow()

    /**
     * 收到的消息
     */
    data class IncomingMessage(
        val playerId: String,
        val playerIndex: Int,  // 玩家在房间中的位置（0=房主, 1/2/3=加入者）
        val message: GameMessage
    )

    /**
     * 启动服务器
     */
    fun start(scope: CoroutineScope) {
        serverSocket = ServerSocket(port)

        // 接受连接
        scope.launch(Dispatchers.IO) {
            var nextIndex = 1  // 0 是房主自己
            while (isActive && nextIndex < maxPlayers) {
                try {
                    val client = serverSocket!!.accept()
                    val playerId = "player_${nextIndex}"
                    val connection = ClientConnection(client, playerId)
                    clients[playerId] = connection

                    // 读消息
                    launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val msg = json.decodeFromString<GameMessage>(line!!)
                                _messages.emit(IncomingMessage(playerId, nextIndex, msg))
                            }
                        } catch (e: Exception) {
                            // 客户端断开，广播离开消息
                            _messages.emit(IncomingMessage(
                                playerId, nextIndex,
                                GameMessage.create(MessageType.PLAYER_LEAVE, mapOf("playerId" to playerId))
                            ))
                        }
                    }

                    nextIndex++
                } catch (e: Exception) {
                    if (!isActive) break
                }
            }
        }
    }

    /**
     * 发送消息给指定玩家
     */
    fun sendTo(playerId: String, type: String, data: Any? = null) {
        val msg = GameMessage.create(type, data)
        clients[playerId]?.send(msg)
    }

    /**
     * 广播消息给所有客户端（不含房主自己）
     */
    fun broadcast(type: String, data: Any? = null) {
        val msg = GameMessage.create(type, data)
        clients.values.forEach { it.send(msg) }
    }

    fun getConnectedCount(): Int = clients.size + 1  // +1 房主自己
    fun isConnected(playerId: String): Boolean = playerId == myPlayerId || clients.containsKey(playerId)

    fun stop() {
        clients.values.forEach { it.close() }
        clients.clear()
        serverSocket?.close()
    }

    private inner class ClientConnection(
        private val socket: Socket,
        val playerId: String
    ) {
        private val writer = PrintWriter(socket.getOutputStream(), true)

        fun send(msg: GameMessage) {
            try {
                writer.println(json.encodeToString(GameMessage.serializer(), msg))
            } catch (e: Exception) { /* 客户端已断开 */ }
        }

        fun close() {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}