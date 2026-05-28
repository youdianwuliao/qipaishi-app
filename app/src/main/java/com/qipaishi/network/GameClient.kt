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
import java.net.Socket

/**
 * TCP 游戏客户端（加入者端）
 *
 * 连接房主的 GameServer，收发游戏消息。
 */
class GameClient(
    private val hostAddress: String,
    private val port: Int,
    private val playerId: String,
    private val playerName: String,
    private val points: Int
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _messages = MutableSharedFlow<GameMessage>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<GameMessage> = _messages.asSharedFlow()

    private val _connected = MutableSharedFlow<Boolean>(replay = 1)
    val connected: SharedFlow<Boolean> = _connected.asSharedFlow()

    /**
     * 连接服务器
     */
    fun connect(onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                socket = Socket(hostAddress, port).also {
                    it.soTimeout = 0  // 不超时
                }
                writer = PrintWriter(socket!!.getOutputStream(), true)
                _connected.emit(true)

                // 发送加入请求
                send(MessageType.JOIN, JoinRequest(playerId, playerName, points))

                // 读消息循环
                val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    try {
                        val msg = json.decodeFromString<GameMessage>(line!!)
                        _messages.emit(msg)
                    } catch (e: Exception) {
                        // 解析失败跳过
                    }
                }

                // 连接断开
                _connected.emit(false)
                onResult(false, "与服务器断开连接")
            } catch (e: Exception) {
                _connected.emit(false)
                onResult(false, "连接失败: ${e.message}")
            }
        }
    }

    /**
     * 发送消息
     */
    fun send(type: String, data: Any? = null) {
        try {
            val msg = GameMessage.create(type, data)
            val line = json.encodeToString(GameMessage.serializer(), msg)
            writer?.println(line)
        } catch (e: Exception) {
            // 发送失败
        }
    }

    fun disconnect() {
        scope.cancel()
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}