package flashtanki.server.battles.weapons

import flashtanki.server.battles.*
import flashtanki.server.client.send
import flashtanki.server.client.toJson
import flashtanki.server.client.toVector
import flashtanki.server.client.weapons.thunder.Fire
import flashtanki.server.client.weapons.thunder.FireStatic
import flashtanki.server.client.weapons.thunder.FireTarget
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemWeapon
import flashtanki.server.math.Vector3
import flashtanki.server.math.Vector3Constants
import flashtanki.server.math.distanceTo

class ThunderWeaponHandler(
  player: BattlePlayer,
  weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
  suspend fun fire(fire: Fire) {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    Command(CommandName.Shot, tank.id, fire.toJson()).send(battle.players.exclude(player).ready())
  }

  suspend fun fireStatic(static: FireStatic) {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    processSplashTargets(static.hitPoint.toVector(), static.splashTargetIds, static.splashTargetDistances)

    Command(CommandName.ShotStatic, tank.id, static.toJson()).send(battle.players.exclude(player).ready())
  }

  suspend fun fireTarget(target: FireTarget) {
    val sourceTank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    val targetTank = battle.players
      .mapNotNull { player -> player.tank }
      .single { tank -> tank.id == target.target }
    if(targetTank.state != TankState.Active) return

    val damage = damageCalculator.calculate(sourceTank, targetTank)
    battle.damageProcessor.dealDamage(sourceTank, targetTank, damage.damage, damage.isCritical)

    processSplashTargets(target.hitPointWorld.toVector(), target.splashTargetIds, target.splashTargetDistances)

    Command(CommandName.ShotTarget, sourceTank.id, target.toJson()).send(battle.players.exclude(player).ready())
  }

  private suspend fun processSplashTargets(hitPoint: Vector3, ids: List<String>, distances: List<String>) {
    val sourceTank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    ids.forEach { id ->
      val targetTank = battle.players
        .mapNotNull { player -> player.tank }
        .single { tank -> tank.id == id }
      if(targetTank.state != TankState.Active) return

      val distance = hitPoint.distanceTo(targetTank.position) * Vector3Constants.TO_METERS
      val damage = damageCalculator.calculate(sourceTank.weapon, distance, splash = true)
      if(damage.damage < 0) return@forEach

      battle.damageProcessor.dealDamage(sourceTank, targetTank, damage.damage, damage.isCritical)
    }
  }
}
