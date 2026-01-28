package flashtanki.server.battles

import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName

interface IMineProcessor {
  val battle: Battle
  val mines: MutableMap<Int, BattleMine>

  fun incrementId()
  suspend fun spawn(mine: BattleMine)
  suspend fun deactivateAll(player: BattlePlayer, native: Boolean = true)
}

class MineProcessor(
  override val battle: Battle
) : IMineProcessor {
  override val mines: MutableMap<Int, BattleMine> = mutableMapOf()
  var nextId: Int = 0

  override fun incrementId() {
    nextId++
  }

  override suspend fun spawn(mine: BattleMine) {
    mines[mine.id] = mine
    mine.spawn()
  }

  override suspend fun deactivateAll(player: BattlePlayer, native: Boolean) {
    val minesToRemove = mines.values.filter { it.owner == player }.toList()

    if (minesToRemove.isEmpty()) return

    if (native) {
      Command(CommandName.RemoveMines, player.user.username).sendTo(battle)
      minesToRemove.forEach { mines.remove(it.id) }
    } else {
      minesToRemove.forEach { it.deactivate() }
    }
  }
}
