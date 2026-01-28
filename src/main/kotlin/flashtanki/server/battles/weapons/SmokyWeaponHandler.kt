package flashtanki.server.battles.weapons

import flashtanki.server.battles.*
import flashtanki.server.client.send
import flashtanki.server.client.weapons.smoky.Fire
import flashtanki.server.client.weapons.smoky.FireStatic
import flashtanki.server.client.weapons.smoky.FireTarget
import flashtanki.server.client.weapons.smoky.ShotTarget
import flashtanki.server.client.toJson
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemWeapon

class SmokyWeaponHandler(
  player: BattlePlayer,
  weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
  private var lastIsCritical = false

  suspend fun fire(fire: Fire) {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    Command(CommandName.Shot, tank.id, fire.toJson()).send(battle.players.exclude(player).ready())
  }

  suspend fun fireStatic(static: FireStatic) {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    Command(CommandName.ShotStatic, tank.id, static.toJson()).send(battle.players.exclude(player).ready())
  }

  suspend fun fireTarget(target: FireTarget) {
    val sourceTank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    val targetTank = battle.players
      .mapNotNull { player -> player.tank }
      .single { tank -> tank.id == target.target }
      .player.tank ?: throw Exception("No Tank")
    if(targetTank.state != TankState.Active) return

    val randomValue = (1..100).random()

    val isCritical = randomValue <= 50 && !lastIsCritical

    lastIsCritical = isCritical

    val damageResult = damageCalculator.calculate(sourceTank, targetTank)

    if (isCritical) {
      val newDamage = damageResult.damage * 2.0
      battle.damageProcessor.dealDamage(sourceTank, targetTank, newDamage, true)
    } else {
      battle.damageProcessor.dealDamage(sourceTank, targetTank, damageResult.damage, false)
    }

    val shot = ShotTarget(target, damageResult.weakening, isCritical)
    Command(CommandName.ShotTarget, sourceTank.id, shot.toJson()).send(battle.players.exclude(player).ready())
  }
}
