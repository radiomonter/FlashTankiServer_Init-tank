package flashtanki.server.battles.bonus

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import flashtanki.server.BonusType
import flashtanki.server.battles.Battle
import flashtanki.server.battles.BattleTank
import flashtanki.server.battles.effect.RepairKitEffect
import flashtanki.server.math.Quaternion
import flashtanki.server.math.Vector3

class BattleRepairKitBonus(battle: Battle, id: Int, position: Vector3, rotation: Quaternion) :
  BattleBonus(battle, id, position, rotation) {
  override val type: BonusType = BonusType.Health

  override suspend fun activate(tank: BattleTank) {
    val effect = RepairKitEffect(tank)
    tank.effects.add(effect)
    effect.run()
    battle.spawnBonusAfterTake(type, 103.seconds, position, rotation)
  }
}