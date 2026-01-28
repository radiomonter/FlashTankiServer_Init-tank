package flashtanki.server.battles.bonus

import flashtanki.server.battles.Battle
import flashtanki.server.battles.BattleTank
import flashtanki.server.battles.sendTo
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.quests.TakeBonusQuest
import flashtanki.server.quests.questOf

interface IBonusProcessor {
  val battle: Battle
  val bonuses: MutableMap<Int, BattleBonus>

  fun incrementId()
  suspend fun spawn(bonus: BattleBonus)
  suspend fun activate(bonus: BattleBonus, tank: BattleTank)
}

class BonusProcessor(
  override val battle: Battle
) : IBonusProcessor {
  override val bonuses: MutableMap<Int, BattleBonus> = mutableMapOf()

  var nextId: Int = 0
    private set

  override fun incrementId() {
    nextId++
  }

  override suspend fun spawn(bonus: BattleBonus) {
    bonuses[bonus.id] = bonus
    bonus.spawn()
  }

  override suspend fun activate(bonus: BattleBonus, tank: BattleTank) {
    bonus.cancelRemove()
    bonus.activate(tank)
    Command(CommandName.ActivateBonus, bonus.key).sendTo(battle)

    bonuses.remove(bonus.id)

    tank.player.user.questOf<TakeBonusQuest> { quest -> quest.bonus == bonus.type }?.let { quest ->
      quest.current++
      tank.socket.updateQuests()
      quest.updateProgress()
    }
  }
}
