package com.qipaishi.game.mahjong.model

/**
 * 牌墙：136 张标准麻将，不含花牌
 */
object TileWall {

    /**
     * 生成完整 136 张牌
     * 万/条/饼 各 36 张（1-9 × 4）
     * 风 16 张（东南西北 × 4）
     * 箭 12 张（中发白 × 4）
     */
    fun full(): List<Tile> {
        val tiles = mutableListOf<Tile>()

        // 数牌 1-9 × 4
        for (suit in listOf(Tile.Suit.WAN, Tile.Suit.TIAO, Tile.Suit.BING)) {
            for (value in 1..9) {
                for (copy in 0..3) {
                    tiles.add(Tile(suit, value, copy))
                }
            }
        }

        // 风牌
        for (value in 1..4) {
            for (copy in 0..3) {
                tiles.add(Tile(Tile.Suit.FENG, value, copy))
            }
        }

        // 箭牌
        for (value in 1..3) {
            for (copy in 0..3) {
                tiles.add(Tile(Tile.Suit.JIAN, value, copy))
            }
        }

        return tiles
    }

    /** 洗牌 Fisher-Yates */
    fun shuffle(tiles: List<Tile>): List<Tile> {
        val shuffled = tiles.toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = (0..i).random()
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }
        return shuffled
    }

    /** 发牌：庄家 14 张，其余 13 张，剩余牌墙 */
    fun deal(): DealResult {
        val shuffled = shuffle(full())
        val hands = listOf(
            shuffled.subList(0, 14).sorted(),    // 庄家 14 张
            shuffled.subList(14, 27).sorted(),   // 南 13 张
            shuffled.subList(27, 40).sorted(),   // 西 13 张
            shuffled.subList(40, 53).sorted()    // 北 13 张
        )
        val wall = shuffled.subList(53, shuffled.size).toMutableList()
        return DealResult(hands, wall)
    }

    /**
     * 摸牌：从牌墙尾部取一张
     */
    fun draw(wall: MutableList<Tile>): Pair<Tile?, MutableList<Tile>> {
        if (wall.isEmpty()) return null to wall
        val tile = wall.removeLast()
        return tile to wall
    }

    data class DealResult(
        val hands: List<List<Tile>>,
        val wall: MutableList<Tile>
    )
}