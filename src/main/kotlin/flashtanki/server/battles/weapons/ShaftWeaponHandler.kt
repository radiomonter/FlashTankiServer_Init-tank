package flashtanki.server.battles.weapons

import flashtanki.server.battles.*
import flashtanki.server.client.send
import flashtanki.server.client.weapons.shaft.FireTarget
import flashtanki.server.client.weapons.shaft.ShotTarget
import flashtanki.server.client.toJson
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemWeapon
import kotlin.random.Random

class ShaftWeaponHandler(
  player: BattlePlayer,
  weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
  private val random = Random.Default

  suspend fun startEnergyDrain(time: Int) {
    val tank = player.tank ?: throw Exception("No Tank")

    // PTT(Dr1llfix)
  }

  suspend fun enterSnipingMode() {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    Command(CommandName.ClientEnterSnipingMode, tank.id).send(battle.players.exclude(player).ready())
  }

  suspend fun exitSnipingMode() {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    Command(CommandName.ClientExitSnipingMode, tank.id).send(battle.players.exclude(player).ready())
  }

  suspend fun fireArcade(target: FireTarget) {
    val sourceTank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    if(target.target != null) {
      val targetTank = battle.players
        .mapNotNull { player -> player.tank }
        .single { tank -> tank.id == target.target }
      if(targetTank.state != TankState.Active) return

      val randomDamage = random.nextInt(34, 56).coerceAtMost(60).toDouble()
      battle.damageProcessor.dealDamage(sourceTank, targetTank, randomDamage, false)
    }

    val shot = ShotTarget(target, 5.0)
    Command(CommandName.ShotTarget, sourceTank.id, shot.toJson()).send(battle.players.exclude(player).ready())
  }

  suspend fun fireSniping(target: FireTarget) {
    val sourceTank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    if(target.target != null) {
      val targetTank = battle.players
        .mapNotNull { player -> player.tank }
        .single { tank -> tank.id == target.target }
      if(targetTank.state != TankState.Active) return

      val randomDamage = random.nextInt(70, 98)

      battle.damageProcessor.dealDamage(sourceTank, targetTank, randomDamage.toDouble(), false)
    }

    val shot = ShotTarget(target, 5.0)
    Command(CommandName.ShotTarget, sourceTank.id, shot.toJson()).send(battle.players.exclude(player).ready())
  }
}

