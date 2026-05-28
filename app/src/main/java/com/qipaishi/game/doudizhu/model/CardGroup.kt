package com.qipaishi.game.doudizhu.model

/**
 * 牌型枚举
 */
enum class CardGroupType(val description: String) {
    SINGLE("单张"),
    PAIR("对子"),
    TRIPLE("三不带"),
    TRIPLE_ONE("三带一"),
    TRIPLE_TWO("三带二"),
    STRAIGHT("顺子"),
    DOUBLE_STRAIGHT("连对"),
    AIRPLANE("飞机不带"),
    AIRPLANE_SINGLE("飞机带单"),
    AIRPLANE_PAIR("飞机带双"),
    FOUR_TWO("四带二"),
    BOMB("炸弹"),
    ROCKET("火箭")
}

/**
 * 牌型：表示一组合法的出牌
 *
 * @param type 牌型
 * @param cards 按大小排序的牌列表
 * @param keyRank 用于比较大小的关键点数（炸弹比点数、顺子比最大牌等）
 */
data class CardGroup(
    val type: CardGroupType,
    val cards: List<Card>,
    val keyRank: Int
)