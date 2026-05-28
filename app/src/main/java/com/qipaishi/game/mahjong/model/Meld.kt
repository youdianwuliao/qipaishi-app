package com.qipaishi.game.mahjong.model

/**
 * 副露（吃/碰/杠）
 */
data class Meld(
    val type: MeldType,
    val tiles: List<Tile>,       // 组成副露的牌
    val fromPlayer: Int? = null  // 从哪个玩家拿的牌（暗杠为 null）
)

enum class MeldType(val description: String) {
    CHI("吃"),
    PENG("碰"),
    GANG_OPEN("明杠"),     // 杠别人的打牌
    GANG_HIDDEN("暗杠"),   // 手里 4 张相同的自己杠
    GANG_ADDED("加杠")     // 碰了之后自己又摸到第 4 张
}

/**
 * 副露判定工具
 */
object MeldDetector {

    /**
     * 检查能否吃（只能吃上家的牌）
     */
    fun canChi(hand: List<Tile>, discard: Tile): List<List<Tile>> {
        if (!discard.isNumberSuit) return emptyList()
        val results = mutableListOf<List<Tile>>()
        val suit = discard.suit
        val v = discard.value

        // 吃法：v-2, v-1, v  /  v-1, v, v+1  /  v, v+1, v+2
        val patterns = listOf(
            listOf(v - 2, v - 1, v),
            listOf(v - 1, v, v + 1),
            listOf(v, v + 1, v + 2)
        )

        for (pattern in patterns) {
            if (pattern.any { it < 1 || it > 9 }) continue
            val needed = pattern.filter { it != v }
            if (needed.all { n -> hand.any { t -> t.suit == suit && t.value == n } }) {
                // 找到对应的牌
                val chiTiles = pattern.map { n ->
                    if (n == v) discard
                    else hand.first { it.suit == suit && it.value == n }
                }
                results.add(chiTiles)
            }
        }
        return results
    }

    /**
     * 检查能否碰
     */
    fun canPeng(hand: List<Tile>, discard: Tile): Boolean {
        return hand.count { it.suit == discard.suit && it.value == discard.value } >= 2
    }

    /**
     * 检查能否明杠（手中有 3 张相同的）
     */
    fun canGangOpen(hand: List<Tile>, discard: Tile): Boolean {
        return hand.count { it.suit == discard.suit && it.value == discard.value } >= 3
    }

    /**
     * 检查能否暗杠（手中有 4 张相同的）
     */
    fun findHiddenGang(hand: List<Tile>): List<Tile>? {
        val groups = hand.groupBy { it.suit to it.value }
        val gang = groups.entries.find { it.value.size == 4 }
        return gang?.value
    }

    /**
     * 检查能否加杠（已碰的牌，手里又摸到第 4 张）
     */
    fun findAddedGang(hand: List<Tile>, melds: List<Meld>): Tile? {
        for (meld in melds) {
            if (meld.type == MeldType.PENG) {
                val target = meld.tiles.first()
                val match = hand.find { it.suit == target.suit && it.value == target.value }
                if (match != null) return match
            }
        }
        return null
    }
}