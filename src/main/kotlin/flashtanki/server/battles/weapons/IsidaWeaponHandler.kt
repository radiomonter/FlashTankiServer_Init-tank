package flashtanki.server.battles.weapons

import flashtanki.server.battles.*
import flashtanki.server.battles.mode.TeamModeHandler
import flashtanki.server.client.send
import flashtanki.server.client.weapons.isida.IsidaFireMode
import flashtanki.server.client.weapons.isida.ResetTarget
import flashtanki.server.client.weapons.isida.SetTarget
import flashtanki.server.client.weapons.isida.StartFire
import flashtanki.server.client.toJson
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemWeapon

class IsidaWeaponHandler(
    player: BattlePlayer,
    weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
    private var fireStarted = false

    suspend fun setTarget(setTarget: SetTarget) {
        val tank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle

        Command(CommandName.ClientSetTarget, tank.id, setTarget.toJson()).send(battle.players.exclude(player).ready())
    }

    suspend fun resetTarget(resetTarget: ResetTarget) {
        val tank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle

        Command(CommandName.ClientResetTarget, tank.id, resetTarget.toJson()).send(battle.players.exclude(player).ready())
    }

    suspend fun fireStart(startFire: StartFire) {
        val sourceTank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle

        val targetTank = battle.players
            .mapNotNull { player -> player.tank }
            .single { tank -> tank.id == startFire.target }
        if (targetTank.state != TankState.Active) return

        //val isFriendlyFireEnabled = battle.properties[BattleProperty.FriendlyFireEnabled] == true

        val fireMode = when {
            //isFriendlyFireEnabled -> IsidaFireMode.Damage // Allow damaging allies if FriendlyFireEnabled is true
            battle.modeHandler is TeamModeHandler -> {
                if (targetTank.player.team == sourceTank.player.team) IsidaFireMode.Heal
                else IsidaFireMode.Damage
            }
            else -> IsidaFireMode.Damage
        }

        // TODO: (Darl1xVFX): Damage timing is not checked on the server, exploitation is possible
        if (fireStarted) {
            val damage = damageCalculator.calculate(sourceTank, targetTank)
            when (fireMode) {
                IsidaFireMode.Damage -> {
                    battle.damageProcessor.dealDamage(sourceTank, targetTank, damage.damage, isCritical = false)
                    battle.damageProcessor.heal(sourceTank, damage.damage)

                    // Display healed health if the tank is not at full health
                    if (sourceTank.health < sourceTank.hull.modification.maxHealth) {
                        Command(
                            CommandName.DamageTank,
                            sourceTank.id,
                            damage.damage.toInt().toString(),
                            DamageType.Heal.key
                        ).send(sourceTank)
                    }
                }

                IsidaFireMode.Heal -> {
                    // Check if the target tank is already at full health
                    val isTargetAtFullHealth = targetTank.health >= targetTank.hull.modification.maxHealth

                    // Skip healing if the target tank is already at full health
                    if (!isTargetAtFullHealth) {
                        battle.damageProcessor.heal(sourceTank, targetTank, damage.damage)

                        // Display healed health if the tank is not at full health
                        if (targetTank.health < targetTank.hull.modification.maxHealth) {
                            Command(
                                CommandName.DamageTank,
                                targetTank.id,
                                damage.damage.toInt().toString(),
                                DamageType.Heal.key
                            ).send(targetTank)
                        }
                    }
                }
            }

            return
        }

        fireStarted = true

        val setTarget = SetTarget(
            physTime = startFire.physTime,
            target = startFire.target,
            incarnation = startFire.incarnation,
            localHitPoint = startFire.localHitPoint,
            actionType = fireMode
        )

        Command(CommandName.ClientSetTarget, sourceTank.id, setTarget.toJson()).send(battle.players.exclude(player).ready())
    }

    suspend fun fireStop() {
        val tank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle

        fireStarted = false

        Command(CommandName.ClientStopFire, tank.id).send(battle.players.exclude(player).ready())
    }
}
