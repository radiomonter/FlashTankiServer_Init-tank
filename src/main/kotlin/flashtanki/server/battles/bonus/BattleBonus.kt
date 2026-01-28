package flashtanki.server.battles.bonus

import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import flashtanki.server.BonusType
import flashtanki.server.battles.Battle
import flashtanki.server.battles.BattleTank
import flashtanki.server.battles.sendTo
import flashtanki.server.client.toJson
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.extensions.launchDelayed
import flashtanki.server.math.Quaternion
import flashtanki.server.math.Vector3

abstract class BattleBonus(
  val battle: Battle,
  val id: Int,
  val position: Vector3,
  val rotation: Quaternion
) {
  abstract val type: BonusType

  var spawnTime: Instant? = null
    protected set

  val aliveFor: Duration
    get() {
      val spawnTime = spawnTime ?: throw IllegalStateException("Bonus is not spawned")
      return Clock.System.now() - spawnTime
    }

  var removeJob: Job? = null
    protected set

  val key: String
    get() = "${type.bonusKey}_$id"

  fun cancelRemove() {
    removeJob?.cancel()
    removeJob = null
  }
  
  suspend fun removeThis() {
      battle.bonusProcessor.bonuses.remove(id)
      Command(CommandName.RemoveBonus, key).sendTo(battle)
  }

  open suspend fun spawn() {
    Command(
      CommandName.SpawnBonus,
      SpawnBonusDatta(
        id = key,
        x = position.x,
        y = position.y,
        z = position.z + 400.0,
        disappearing_time = -1
      ).toJson()
    ).sendTo(battle)
    spawnTime = Clock.System.now()
  }

  abstract suspend fun activate(tank: BattleTank)
}
