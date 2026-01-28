package flashtanki.server.battles.effect

import kotlin.time.Duration.Companion.seconds
import flashtanki.server.battles.BattleMine
import flashtanki.server.battles.BattleTank

class MineEffect(
  tank: BattleTank
) : TankEffect(
  tank,
  duration = null,
  cooldown = 20.seconds
) {
  override val info: EffectInfo
    get() = EffectInfo(
      id = 5,
      name = "mine"
    )

  override suspend fun activate() {
    val battle = tank.battle

    val mine = BattleMine(battle.mineProcessor.nextId, tank.player, tank.position)

    battle.mineProcessor.incrementId()
    battle.mineProcessor.spawn(mine)
  }
}
