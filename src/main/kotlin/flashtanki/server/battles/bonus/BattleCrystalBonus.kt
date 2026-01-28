package flashtanki.server.battles.bonus

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import flashtanki.server.BonusType
import flashtanki.server.battles.Battle
import flashtanki.server.battles.BattleTank
import flashtanki.server.battles.sendTo
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.math.Quaternion
import flashtanki.server.math.Vector3

class BattleCrystalBonus(battle: Battle, id: Int, position: Vector3, rotation: Quaternion) :
  BattleBonus(battle, id, position, rotation) {
  override val type: BonusType = BonusType.Crystal

  override suspend fun spawn() {
    delay(0.seconds.inWholeMilliseconds)
    super.spawn()
  }

  override suspend fun activate(tank: BattleTank) {
    tank.player.user.crystals += 10 //10
    tank.socket.updateCrystals()
  }
}
