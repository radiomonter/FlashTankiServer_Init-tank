package flashtanki.server.battles.effect

import kotlin.time.Duration.Companion.seconds
import flashtanki.server.battles.bonus.*
import flashtanki.server.math.Quaternion
import flashtanki.server.math.Vector3
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import flashtanki.server.BonusType
import flashtanki.server.toVector
import flashtanki.server.battles.BattleTank

class DropGoldBoxEffect(
  tank: BattleTank
) : TankEffect(
  tank,
  duration = null,
  cooldown = 5.seconds
) {
  override val info: EffectInfo
    get() = EffectInfo(
      id = 6,
      name = "gold"
    )

  override suspend fun activate() {
    val battle = tank.battle
    battle.spawnGoldBonus(if (tank.socket.showGoldAuthor) getUsername() + " сбросил золотой ящик" else "", true)
  }
  
  suspend fun getUsername() : String {
     return (tank.socket.user?.username ?: "undefined")
  }
}
