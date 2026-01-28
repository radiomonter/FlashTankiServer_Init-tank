package flashtanki.server.battles.weapons

import flashtanki.server.battles.*
import flashtanki.server.battles.mode.DeathmatchModeHandler
import flashtanki.server.client.send
import flashtanki.server.client.weapons.flamethrower.StartFire
import flashtanki.server.client.weapons.flamethrower.StopFire
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemWeapon
import kotlinx.coroutines.delay
import kotlin.random.Random

class FlamethrowerWeaponHandler(
  player: BattlePlayer,
  weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
  private val random = Random.Default
  private var fire = "0"
  private var accumulatedDamage = mutableMapOf<String, Double>()

  suspend fun fireStart(startFire: StartFire) {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    Command(CommandName.ClientStartFire, tank.id).send(battle.players.exclude(player).ready())
  }

  suspend fun fireTarget(target: flashtanki.server.client.weapons.flamethrower.FireTarget) {
    val sourceTank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle
    fire = "0.7"

    val targetTanks = battle.players
      .mapNotNull { player -> player.tank }
      .filter { tank -> target.targets.contains(tank.id) }
      .filter { tank -> tank.state == TankState.Active }

    targetTanks.forEach { targetTank ->
      if (sourceTank != targetTank && (battle.properties[BattleProperty.FriendlyFireEnabled] ||
                battle.modeHandler is DeathmatchModeHandler || player.team != targetTank.player.team)) {

        val damage = damageCalculator.calculate(sourceTank, targetTank)
        val damageToDeal = damage.damage / random.nextInt(2, 4)

        accumulatedDamage[targetTank.id] = (accumulatedDamage[targetTank.id] ?: 0.0) + damageToDeal

        val param1 = fire
        Command(CommandName.Temperature, targetTank.id, param1).sendTo(battle)

        battle.damageProcessor.dealDamage(sourceTank, targetTank, damage.damage, damage.isCritical)
        delay(1000)
      }
    }
  }

  suspend fun fireStop(stopFire: StopFire) {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle
    val targetTanks = battle.players.mapNotNull { it.tank }.filter { it.state == TankState.Active }

    fire = "0"

    Command(CommandName.ClientStopFire, tank.id).send(battle.players.exclude(player).ready())
    targetTanks.forEach { targetTank ->
      if (tank != targetTank && (battle.properties[BattleProperty.FriendlyFireEnabled] ||
                battle.modeHandler is DeathmatchModeHandler || player.team != targetTank.player.team)) {
        while ((accumulatedDamage[targetTank.id] ?: 0.0) > 0 && stopAllOperations()) {
          val damage = damageCalculator.calculate(tank, targetTank)
          val damageToDeal = damage.damage / random.nextInt(2, 4)
          battle.damageProcessor.dealDamage(player.tank!!, targetTank, damageToDeal, isCritical = false)
          accumulatedDamage[targetTank.id] = (accumulatedDamage[targetTank.id] ?: 0.0) - damageToDeal
          val currentTemperature = (accumulatedDamage[targetTank.id] ?: 0.0).coerceIn(0.0, 0.5)
          Command(CommandName.Temperature, targetTank.id, currentTemperature.toString()).sendTo(battle)
          if (targetTank.health < 2.0) {
            Command(CommandName.Temperature, targetTank.id, fire).sendTo(battle)
            break
          }
          delay(1500)
        }
        Command(CommandName.Temperature, targetTank.id, fire).sendTo(battle)
      }
    }
  }
  fun stopAllOperations(): Boolean {
    return false
  }
}
