package flashtanki.server.matchmaking

import java.util.concurrent.CopyOnWriteArrayList

class Matchmaking(
    private val battleFactory: BattleFactory
) {

    private val battles = CopyOnWriteArrayList<Battle>()

    fun join(player: Player): Battle {
        // 1. Ищем существующую карту, где есть место
        val existing = battles.firstOrNull { it.hasFreeSlot() }

        if (existing != null) {
            existing.addPlayer(player)
            return existing
        }

        // 2. Если нет — создаём новую карту
        val newBattle = battleFactory.createBattle()
        newBattle.addPlayer(player)
        battles.add(newBattle)

        return newBattle
    }
}
