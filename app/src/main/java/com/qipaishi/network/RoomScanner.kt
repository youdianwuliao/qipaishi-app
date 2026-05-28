package com.qipaishi.network

import com.qipaishi.network.protocol.RoomInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * 房间扫描器（客户端端）
 *
 * 加入 UDP 组播组，监听局域网房间广播。
 * 房间超过 5 秒没收到心跳则从列表移除。
 */
class RoomScanner {

    private val multicastGroup = InetAddress.getByName(RoomBroadcaster.MULTICAST_ADDRESS)
    private val address = InetSocketAddress(multicastGroup, RoomBroadcaster.MULTICAST_PORT)
    private val socket = MulticastSocket(RoomBroadcaster.MULTICAST_PORT)
    private val json = Json { ignoreUnknownKeys = true }

    private val _rooms = MutableStateFlow<Map<String, RoomState>>(emptyMap())
    val rooms: StateFlow<Map<String, RoomState>> = _rooms.asStateFlow()

    private var job: Job? = null

    /**
     * 开始扫描
     */
    fun start(scope: CoroutineScope) {
        // 加入组播组
        socket.joinGroup(InetSocketAddress(multicastGroup, 0), NetworkInterface.getNetworkInterfaces().nextElement())

        // 接收线程
        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val message = String(packet.data, 0, packet.length)
                    val roomInfo = json.decodeFromString(RoomInfo.serializer(), message)

                    // 收到广播，刷新心跳
                    val current = _rooms.value.toMutableMap()
                    val roomState = current[roomInfo.roomId]
                    if (roomState == null) {
                        // 新房间
                        current[roomInfo.roomId] = RoomState(roomInfo, System.currentTimeMillis())
                    } else {
                        // 更新人数（只读属性需重建）
                        current[roomInfo.roomId] = RoomState(
                            roomInfo = roomInfo,  // 用最新的 roomInfo（人数可能变了）
                            lastSeen = System.currentTimeMillis()
                        )
                    }
                    _rooms.value = current
                } catch (e: Exception) {
                    // 解析失败忽略
                }
            }
        }

        // 清理过期房间（5 秒没心跳）
        scope.launch {
            while (isActive) {
                delay(2000)
                val now = System.currentTimeMillis()
                val current = _rooms.value.toMutableMap()
                current.entries.removeAll { now - it.value.lastSeen > 5000 }
                if (current.size != _rooms.value.size) {
                    _rooms.value = current
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        socket.leaveGroup(InetSocketAddress(multicastGroup, 0), NetworkInterface.getNetworkInterfaces().nextElement())
        socket.close()
    }

    data class RoomState(
        val roomInfo: RoomInfo,
        val lastSeen: Long
    )
}