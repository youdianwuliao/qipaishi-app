package com.qipaishi.network

import com.qipaishi.network.protocol.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * 房间广播器（房主端）
 *
 * 每个房间创建后启动，定时 1 秒向局域网组播地址发送房间信息。
 * 客户端 RoomScanner 收到后列出可用房间。
 */
class RoomBroadcaster(
    private val roomInfo: RoomInfo
) {
    private val multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS)
    private val socket = DatagramSocket()
    private val json = Json { encodeDefaults = true }
    private var job: Job? = null

    /**
     * 开始广播（在协程中运行）
     */
    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            val message = json.encodeToString(RoomInfo.serializer(), roomInfo)
            val data = message.toByteArray()
            val packet = DatagramPacket(data, data.size, InetSocketAddress(multicastGroup, MULTICAST_PORT))

            while (isActive) {
                try {
                    socket.send(packet)
                } catch (e: Exception) {
                    // 网络异常忽略，继续广播
                }
                delay(1000)  // 1 秒一次
            }
        }
    }

    fun stop() {
        job?.cancel()
        socket.close()
    }

    companion object {
        const val MULTICAST_ADDRESS = "239.255.0.1"
        const val MULTICAST_PORT = 9527
    }
}