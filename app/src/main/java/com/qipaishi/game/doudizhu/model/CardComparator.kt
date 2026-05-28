package com.qipaishi.game.doudizhu.model

/**
 * 出牌大小比较器
 *
 * 核心规则：
 * 1. 火箭打一切
 * 2. 炸弹可以打普通牌型，也可以打更小的炸弹
 * 3. 普通牌型只能同类型、同数量比较
 * 4. 顺子/连对/飞机 比最大一张的点数
 */
object CardComparator {

    /**
     * 判断 newGroup 能否打 lastGroup
     * @param lastGroup 上一轮出的牌（null 表示新一轮，什么都能出）
     */
    fun canBeat(newGroup: CardGroup, lastGroup: CardGroup?): Boolean {
        if (lastGroup == null) return true  // 新一轮，自由出牌

        // 火箭打一切
        if (newGroup.type == CardGroupType.ROCKET) return true

        // 炸弹的情况
        if (newGroup.type == CardGroupType.BOMB) {
            if (lastGroup.type == CardGroupType.ROCKET) return false  // 打不过火箭
            if (lastGroup.type != CardGroupType.BOMB) return true    // 炸弹打普通牌型
            return newGroup.keyRank > lastGroup.keyRank              // 炸弹比点数
        }

        // 普通牌型：必须同类型
        if (newGroup.type != lastGroup.type) return false

        // 必须同数量（顺子长度要一样）
        if (newGroup.cards.size != lastGroup.cards.size) return false

        // 比 keyRank
        return newGroup.keyRank > lastGroup.keyRank
    }

    /**
     * 给一组手牌里的可行牌型排序（炸弹>普通，大的>小的）
     * 用于 AI 出牌建议
     */
    fun rankBeatOptions(
        options: List<CardGroup>,
        lastGroup: CardGroup?
    ): List<CardGroup> {
        return options
            .filter { canBeat(it, lastGroup) }
            .sortedWith(compareBy(
                { if (it.type == CardGroupType.BOMB || it.type == CardGroupType.ROCKET) 0 else 1 },
                { -it.keyRank }
            ))
    }
}