package flashtanki.server.battles.weapons

import flashtanki.server.battles.*
import flashtanki.server.client.send
import flashtanki.server.client.toJson
import flashtanki.server.client.weapons.railgun_terminator.FireTarget
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemWeapon

class Railgun_TERMINATORWeaponHandler(
  player: BattlePlayer,
  weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
  suspend fun fireStart() {
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    Command(CommandName.StartFire, tank.id).send(battle.players.exclude(player).ready())
  }

  suspend fun fireTarget(target: FireTarget) {
    val sourceTank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    // Preserve order of targets
    // TODO(Assasans): Replace with a more efficient algorithm
    val targetTanks = target.targets
      .mapNotNull { username -> battle.players.singleOrNull { player -> player.user.username == username } }
      .mapNotNull { player -> player.tank }
      .filter { tank -> target.targets.contains(tank.id) }
      .filter { tank -> tank.state == TankState.Active }

    targetTanks.forEach { targetTank ->
      val damage = damageCalculator.calculate(sourceTank, targetTank)
      battle.damageProcessor.dealDamage(sourceTank, targetTank, damage.damage, damage.isCritical)
    }

    Command(CommandName.ShotTarget, sourceTank.id, target.toJson()).send(battle.players.exclude(player).ready())
  }
}
